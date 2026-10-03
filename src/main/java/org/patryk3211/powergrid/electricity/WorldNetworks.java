/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.patryk3211.powergrid.electricity;

import com.google.common.collect.Sets;
import io.netty.util.collection.IntObjectHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IMultipartSync;
import org.patryk3211.powergrid.electricity.base.ISynchronizedElement;
import org.patryk3211.powergrid.electricity.sim.*;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePort;
import org.patryk3211.powergrid.electricity.wire.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldNetworks extends SavedData implements NetworkGraph.IGraphModifyHooks {
    public final Level world;
    public final NetworkGraph globalGraph = new NetworkGraph();
    protected final PerformanceCounter perf;

    public final List<ElectricalNetwork> subnetworks = new ArrayList<>();
    public final Map<Integer, TransmissionLine> transmissionLines = new IntObjectHashMap<>();
    public final Map<IWireEndpoint, OwnedFloatingNode> globalExternalNodes = new HashMap<>();

    private final Map<ChunkPos, CheckChunk> expectedInChunks = new ConcurrentHashMap<>();
    private final Map<ChunkPos, CheckChunk> checkForExistence = new ConcurrentHashMap<>();
    private final Map<PartId, TransmissionLinePart> lineParts = new HashMap<>();
    private final Map<OwnedFloatingNode, Set<TransmissionLinePart>> partNodeMap = new HashMap<>();

    private final Map<IWireEndpoint, Set<ServerPlayer>> trackers = new HashMap<>();

    protected final Set<TransmissionLinePart> deferredRewireEntities = new HashSet<>();
    protected final Set<ElectricalNetwork> islandDiscoveryQueue = new HashSet<>();
    private boolean runningDiscovery = false;
    private int syncTicks = 0;

    private CompoundTag nbt;

    private record SyncState(int lod) { }
    private final Map<ServerPlayer, Map<ISynchronizedElement, SyncState>> syncStates = new HashMap<>();

    public WorldNetworks(Level world) {
        this.world = world;
        this.globalGraph.hooks = this;
        this.perf = new PerformanceCounter(world.dimension().location().toString());
    }

    public WorldNetworks(Level world, CompoundTag nbt) {
        this(world);
        this.nbt = nbt;
    }

    void completeLoad() {
        if(nbt != null) {
            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] completeLoad: restoring WorldNetworks SavedData"
            );
            readNbt(nbt);
            nbt = null;
        } else {
            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] completeLoad: no deferred NBT present"
            );
        }
    }

    @Override
    public void lineConnected(TransmissionLine line) {
        var id = line.getId();
        transmissionLines.put(id, line);

        var line1 = findLineMiddle(line.getNode1());
        if(line1 != null)
            line1.splitAt(line.getNode1());
        var line2 = findLineMiddle(line.getNode2());
        if(line2 != null)
            line2.splitAt(line.getNode2());
        scheduleIslandDiscovery(line.getNode1().getNetwork());
        scheduleIslandDiscovery(line.getNode2().getNetwork());
        setDirty();
    }

    private final Set<TransmissionLinePart> pendingTransmissionLineRepair =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** Temporary diagnostics for transmission-line restoration after world load. */
    private final Map<TransmissionLinePart, Integer> transmissionLineRepairAttempts =
            new IdentityHashMap<>();

    public void queueTransmissionLineRepair(TransmissionLinePart part) {
        if (part != null) {
            pendingTransmissionLineRepair.add(part);
            transmissionLineRepairAttempts.putIfAbsent(part, 0);
            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] QUEUED part={} endpoint1={} endpoint2={} line={}",
                    System.identityHashCode(part),
                    part.getEndpoint1(),
                    part.getEndpoint2(),
                    part.getLine()
            );
        }
    }

    @Override
    public void lineDisconnected(TransmissionLine line) {
        transmissionLines.remove(line.getId());
        scheduleIslandDiscovery(line.getNetwork());
        setDirty();
    }

    public static boolean canWeakCouple(TransmissionLine line) {
        return ModdedConfigs.server().electricity.solver.splittingTransmissionLines.get() &&
                line.getResistance() > ModdedConfigs.server().electricity.solver.transmissionLineThreshold.getF();
    }

    public void scheduleIslandDiscovery(ElectricalNetwork network) {
        if(network != null && !runningDiscovery)
            islandDiscoveryQueue.add(network);
    }

    private void runIslandDiscoveryFor(ElectricalNetwork network) {
        var visited = new HashSet<IElectricNode>();
        var islands = new ArrayList<Island>();
        var couplings = new HashMap<CouplingKey, Set<TransmissionLine>>();
        if(ModdedConfigs.logsEnabled())
            PowerGrid.LOGGER.debug("Running island discovery for {}", network);

        var queue = new ArrayList<>(network.getNodes());
        queue.addAll(network.getLeafs());
        while(!queue.isEmpty()) {
            var node = queue.remove(0);
            if(!(node instanceof IElectricNode enode))
                continue;
            if (!visited.add(enode))
                continue;
            Island island = null;
            for(var otherIsland : islands) {
                if(otherIsland.contains(node)) {
                    island = otherIsland;
                    break;
                }
            }
            if(island == null) {
                island = new Island(couplings);
                islands.add(island);
            }
            island.add(enode);
            for(var wire : globalGraph.getWires(enode)) {
                if(wire instanceof TransmissionLine line && canWeakCouple(line)) {
                    // Weak line can split islands.
                    var otherNode = line.getNode1() == node ? line.getNode2() : line.getNode1();
                    Island connectedIsland = null;
                    for(var otherIsland : islands) {
                        if(otherIsland.contains(otherNode)) {
                            connectedIsland = otherIsland;
                            break;
                        }
                    }
                    if(connectedIsland == null) {
                        connectedIsland = new Island(couplings);
                        connectedIsland.add(otherNode);
                        islands.add(connectedIsland);
                    }
                    if(connectedIsland != island) {
                        island.addCoupling(connectedIsland, line);
                        queue.add(otherNode);
                        continue;
                    }
                }
                for(var otherNode : wire.coupledNodes()) {
                    if(otherNode == node)
                        continue;
                    island.add(otherNode);
                    queue.add(otherNode);
                    for(var otherIsland : islands) {
                        if(otherIsland == island)
                            continue;
                        if(otherIsland.contains(otherNode)) {
                            // Merge islands
                            otherIsland.addAll(island);
                            islands.remove(island);
                            island = otherIsland;
                            break;
                        }
                    }
                }
                island.add(wire);
            }
            var remove = new ArrayList<ICouplingNode>();
            for(var coupling : globalGraph.getCouplings(enode)) {
                if(coupling instanceof TransmissionLinePort) {
                    // This should be handled by transmission lines above
                    remove.add(coupling);
                    continue;
                }
                for(var otherNode : coupling.coupledNodes()) {
                    if(otherNode == node)
                        continue;
                    island.add(otherNode);
                    queue.add(otherNode);
                    for(var otherIsland : islands) {
                        if(otherIsland == island)
                            continue;
                        if(otherIsland.contains(otherNode)) {
                            // Merge islands
                            otherIsland.addAll(island);
                            islands.remove(island);
                            island = otherIsland;
                            break;
                        }
                    }
                }
                island.add(coupling);
            }
            remove.forEach(INode::remove);
        }
        if(islands.size() <= 1)
            return;
        for(var island : islands) {
            var islandNetwork = newNetwork();
            islandNetwork.fromElements(island.elements);
        }
        for(var lines : couplings.values()) {
            for(var line : lines) {
                line.setNetwork(null);
                line.makePortPair();
            }
        }
        network.clear();
        subnetworks.remove(network);
        network.cleanup();
    }

    public void preTick() {
        deferredRewireEntities.removeIf(part -> {
            part.refreshEndpointNodes();
            return true;
        });

        if (!pendingTransmissionLineRepair.isEmpty()) {
            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] preTick: pendingParts={}",
                    pendingTransmissionLineRepair.size()
            );
        }
        repairPendingTransmissionLines();

        JunctionWireEndpoint.processNewNodes(world);

        // 以下は既存コード

        runningDiscovery = true;
        for(var network : islandDiscoveryQueue) {
            runIslandDiscoveryFor(network);
        }
        islandDiscoveryQueue.clear();
        runningDiscovery = false;

        var iter2 = transmissionLines.values().iterator();
        var removed = new ArrayList<TransmissionLine>();
        while(iter2.hasNext()) {
            var line = iter2.next();
            if(line.segments.isEmpty()) {
                PowerGrid.LOGGER.warn("Empty transmission line {} dropped during tick", line);
                removed.add(line);
                iter2.remove();
                continue;
            }
            line.tick();
        }
        removed.forEach(TransmissionLine::remove);

        perf.start();
        int multiTick = ModdedConfigs.server().electricity.solver.multiTicks.get();
        var iter = subnetworks.iterator();
        while (iter.hasNext()) {
            var network = iter.next();
            if (network.isEmpty()) {
                iter.remove();
                network.cleanup();
                continue;
            }
            network.prepare(multiTick);
        }
        for(int i = 0; i < multiTick; ++i) {
            // I guess this could go on a thread-pool
            for(var network : subnetworks) {
                network.singleTick();
            }
        }

        perf.end();
    }

    public void postTick() {
        if(world instanceof ServerLevel serverWorld) {
            // Check for line parts existence.
            //
            // IMPORTANT:
            // A missing entity does NOT mean that the wire was destroyed.
            //
            // During world/server reload a BaseWireEntity may temporarily be
            // unavailable even though its TransmissionLinePart is still valid.
            // Therefore we must never remove a line merely because getEntity()
            // returns null.
            var checkIter = checkForExistence.entrySet().iterator();

            while(checkIter.hasNext()) {
                var entry = checkIter.next();
                var chunk = entry.getKey();

                // The chunk isn't loaded yet.
                // Keep the check pending until it is loaded.
                if(!world.hasChunk(chunk.x, chunk.z)) {
                    if(expectedInChunks.containsKey(chunk)) {
                        expectedInChunks.get(chunk).addAll(entry.getValue().entities);
                    } else {
                        entry.getValue().ticks = 0;
                        expectedInChunks.put(chunk, entry.getValue());
                    }
                    checkIter.remove();
                    continue;
                }

                var entityIter = entry.getValue().entities.iterator();

                while(entityIter.hasNext()) {
                    var id = entityIter.next();

                    BaseWireEntity entity = id.getEntity(serverWorld);

                    if(entity == null) {
                        /*
                         * DO NOT remove the TransmissionLine here.
                         *
                         * The entity may simply not have been restored/loaded
                         * yet. Actual destruction is handled explicitly from
                         * BaseWireEntity.remove(RemovalReason).
                         *
                         * Keep the PartId in the check set so that a later
                         * chunk/entity load can resolve it.
                         */
                        continue;
                    }

                    // Entity exists again, so there is nothing to check.
                    entityIter.remove();
                }

                /*
                 * Only remove the bookkeeping entry when every entity that was
                 * being checked has been found.
                 *
                 * Missing entities are intentionally kept pending.
                 */
                if(entry.getValue().entities.isEmpty()) {
                    checkIter.remove();
                }
            }

            // Synchronize state with clients
            if(syncTicks % 5 == 0) {
                syncStates.clear();

                var trackerEntryIter = trackers.entrySet().iterator();
                while(trackerEntryIter.hasNext()) {
                    var entry = trackerEntryIter.next();
                    var playerIter = entry.getValue().iterator();

                    while(playerIter.hasNext()) {
                        var player = playerIter.next();

                        if(player.isRemoved()) {
                            playerIter.remove();
                            continue;
                        }

                        var endpoint = entry.getKey();

                        if(endpoint instanceof BlockWireEndpoint bwe) {
                            var eb = bwe.getElectricBehaviour(world);
                            if (eb == null)
                                continue;

                            var ebPos = eb.getPos();
                            var syncState = new SyncState(
                                    (int) (
                                            Math.sqrt(
                                                    player.distanceToSqr(
                                                            ebPos.getX(),
                                                            ebPos.getY(),
                                                            ebPos.getZ()
                                                    )
                                            ) / 24 + 1
                                    )
                            );

                            if(eb.blockEntity instanceof IMultipartSync multipart) {
                                multipart.forSync(sync -> {
                                    if(sync == null)
                                        return;

                                    syncStates
                                            .computeIfAbsent(player, $ -> new HashMap<>())
                                            .put(sync, syncState);
                                });
                            } else {
                                syncStates
                                        .computeIfAbsent(player, $ -> new HashMap<>())
                                        .put(eb, syncState);
                            }

                        } else if(endpoint instanceof JunctionWireEndpoint je) {
                            var syncEntry = je.makeSyncEntry(world);
                            var jePos = je.getExactPosition(world);

                            if(syncEntry != null) {
                                syncStates
                                        .computeIfAbsent(player, $ -> new HashMap<>())
                                        .put(
                                                syncEntry,
                                                new SyncState(
                                                        (int) (
                                                                Math.sqrt(
                                                                        player.distanceToSqr(jePos)
                                                                ) / 24 + 1
                                                        )
                                                )
                                        );
                            }

                        } else if(endpoint instanceof CircuitBoardEndpoint cbe) {
                            var eb = cbe.getElectricBehaviour(world);
                            if (eb == null)
                                continue;

                            var ebPos = eb.getPos();
                            var syncState = new SyncState(
                                    (int) (
                                            Math.sqrt(
                                                    player.distanceToSqr(
                                                            ebPos.getX(),
                                                            ebPos.getY(),
                                                            ebPos.getZ()
                                                    )
                                            ) / 24 + 1
                                    )
                            );

                            syncStates
                                    .computeIfAbsent(player, $ -> new HashMap<>())
                                    .put(eb, syncState);
                        }
                    }
                }

                syncTicks = 0;
            }

            syncTicks++;
        }
    }

    public void wireEntityDestroyed(BaseWireEntity entity) {
        if(entity == null)
            return;

        var endpoint1 = entity.getEndpoint1();
        var endpoint2 = entity.getEndpoint2();

        if(endpoint1 != null) {
            var node1 = endpoint1.getNode(world);
            var parts = partNodeMap.get(node1);

            if(parts != null) {
                for(var part : List.copyOf(parts)) {
                    if(part.owner == entity) {
                        part.remove();
                    }
                }
            }
        }

        if(endpoint2 != null) {
            var node2 = endpoint2.getNode(world);
            var parts = partNodeMap.get(node2);

            if(parts != null) {
                for(var part : List.copyOf(parts)) {
                    if(part.owner == entity) {
                        part.remove();
                    }
                }
            }
        }
    }

    public ElectricalNetwork newNetwork() {
        var cSolver = ModdedConfigs.server().electricity.solver;
        var backend = cSolver.solverBackend.get();
        if(!backend.isSupported())
            backend = CSolver.SolverBackend.JAVA;
        var network = new GraphedElectricalNetwork(globalGraph, true, backend::create);
        network.maxIterations = hooks -> hooks
                ? cSolver.solverComplexMaxIterations.get()
                : cSolver.solverSimpleMaxIterations.get();
        var rA = cSolver.solverAbsolutePrecision.get();
        var rR = cSolver.solverRelativePrecision.get();
        var rM = cSolver.solverAbsoluteMinimumPrecision.get();
        var sA = cSolver.solverMaxSearchAlpha.get();
        network.setPrecision(rA, rR, rM, sA);
        network.bjtSmoothAlpha = cSolver.bjtLimAlpha.getF();
        network.diodeSmoothAlpha = cSolver.diodeLimAlpha.getF();
        network.triodeLimCathode = cSolver.triodeLimCathode.getF();
        network.triodeLimAnode = cSolver.triodeLimAnode.getF();
        network.triodeLimGrid = cSolver.triodeLimGrid.getF();
        subnetworks.add(network);
        return network;
    }

    public void add(IWireEndpoint endpoint) {
        var node = endpoint.getNode(world);
        globalGraph.addNode(node);
        addAndMigrateNode(endpoint);
    }

    public int connectionCount(IWireEndpoint endpoint) {
        return globalGraph.connectionCount(endpoint.getNode(world));
    }

    @Nullable
    public TransmissionLine findLineMiddle(OwnedFloatingNode node) {
        var parts = partNodeMap.get(node);
        if(parts == null)
            return null;
        for(var part1 : parts) {
            for(var part2 : parts) {
                if(part1 == part2)
                    continue;
                if(part1.getLine() == part2.getLine() && part1.getLine() != null)
                    return part1.getLine();
            }
        }
        return null;
    }

    public void putInNetwork(@NotNull IWireEndpoint endpoint) {
        var node = endpoint.getNode(world);
        add(endpoint);
        var line = findLineMiddle(node);
        if(line != null)
            return;
        if(node.getNetwork() == null)
            endpoint.joinNetwork(world, newNetwork());
    }

    @Nullable
    public ElectricalNetwork prepareForConnection(IWireEndpoint endpoint1, IWireEndpoint endpoint2) {
        var node1 = endpoint1.getNode(world);
        var node2 = endpoint2.getNode(world);

        if(node1 == node2)
            return null;
        if(node1 == null || node2 == null)
            return null;

        add(endpoint1);
        add(endpoint2);

        // Split transmission lines if needed.
        var line1 = findLineMiddle(node1);
        if(line1 != null)
            line1.splitAt(node1);
        var line2 = findLineMiddle(node2);
        if(line2 != null)
            line2.splitAt(node2);

        var net1 = node1.getNetwork();
        var net2 = node2.getNetwork();

        // Put both nodes into the same network.
        ElectricalNetwork network;
        if(net1 == null && net2 == null) {
            network = newNetwork();
            endpoint1.joinNetwork(world, network);
            endpoint2.joinNetwork(world, network);
        } else if(net1 == null) {
            network = net2;
            endpoint1.joinNetwork(world, network);
        } else if(net2 == null) {
            network = net1;
            endpoint2.joinNetwork(world, network);
        } else if(net1 != net2) {
            if(net1.size() >= net2.size()) {
                network = net1;
                network.merge(net2);
            } else {
                network = net2;
                network.merge(net1);
            }
        } else {
            network = net1;
        }

        return network;
    }

    @Nullable
    public ElectricalNetwork prepareForConnection(IWireEndpoint endpoint1, ElectricNode node2) {
        var node1 = endpoint1.getNode(world);

        if(node1 == node2)
            return null;
        if(node1 == null || node2 == null)
            return null;

        add(endpoint1);
        globalGraph.addNode(node2);

        // Split transmission lines if needed.
        var line1 = findLineMiddle(node1);
        if(line1 != null)
            line1.splitAt(node1);

        var net1 = node1.getNetwork();
        var net2 = node2.getNetwork();

        // Put both nodes into the same network.
        ElectricalNetwork network;
        if(net1 == null && net2 == null) {
            network = newNetwork();
            endpoint1.joinNetwork(world, network);
            network.addNode(node2);
        } else if(net1 == null) {
            network = net2;
            endpoint1.joinNetwork(world, network);
        } else if(net2 == null) {
            network = net1;
            network.addNode(node2);
        } else if(net1 != net2) {
            if(net1.size() >= net2.size()) {
                network = net1;
                network.merge(net2);
            } else {
                network = net2;
                network.merge(net1);
            }
        } else {
            network = net1;
        }

        return network;
    }

    public ElectricalNetwork prepareForTransmissionLine(@NotNull OwnedFloatingNode node1, @NotNull OwnedFloatingNode node2, TransmissionLine line, Runnable callback) {
        var endpoint1 = node1.endpoint;
        var endpoint2 = node2.endpoint;

        if(node1 == node2)
            return null;

        var nNode1 = endpoint1.getNode(world);
        if(node1 != nNode1) {
            addAndMigrateNode(node1, nNode1);
            node1 = nNode1;
        }
        var nNode2 = endpoint2.getNode(world);
        if(node2 != nNode2) {
            addAndMigrateNode(node2, nNode2);
            node2 = nNode2;
        }

        add(endpoint1);
        add(endpoint2);

        var net1 = node1.getNetwork();
        var net2 = node2.getNetwork();

        ElectricalNetwork network = line.getNetwork();
        if(network != null) {
            inNetwork(network, node1);
            inNetwork(network, node2);
            callback.run();
            return network;
        }
        if(net1 == null && net2 == null) {
            network = newNetwork();
            endpoint1.joinNetwork(world, network);
            endpoint2.joinNetwork(world, network);
        } else if(net1 == null) {
            network = net2;
            endpoint1.joinNetwork(world, network);
        } else if(net2 == null) {
            network = net1;
            endpoint2.joinNetwork(world, network);
        } else if(net1 != net2) {
            if(net1.size() >= net2.size()) {
                network = net1;
                network.merge(net2);
            } else {
                network = net2;
                network.merge(net1);
            }
        } else {
            network = net1;
        }
        // We must disconnect the line or else graph will be broken.
        globalGraph.disconnect(line.getNode1(), line.getNode2(), line);
        callback.run();
        network.addWire(line);
        return network;
    }

    @Nullable
    public ElectricalNetwork prepareForConnection(@NotNull OwnedFloatingNode node1, @NotNull OwnedFloatingNode node2) {
        var endpoint1 = node1.endpoint;
        var endpoint2 = node2.endpoint;

        if(node1 == node2)
            return null;

        add(endpoint1);
        add(endpoint2);

        // Split transmission lines if needed.
        var line1 = findLineMiddle(node1);
        if(line1 != null)
            line1.splitAt(node1);
        var line2 = findLineMiddle(node2);
        if(line2 != null)
            line2.splitAt(node2);

        var net1 = node1.getNetwork();
        var net2 = node2.getNetwork();

        // Put both nodes into the same network.
        ElectricalNetwork network;
        if(net1 == null && net2 == null) {
            network = newNetwork();
            endpoint1.joinNetwork(world, network);
            endpoint2.joinNetwork(world, network);
        } else if(net1 == null) {
            network = net2;
            endpoint1.joinNetwork(world, network);
        } else if(net2 == null) {
            network = net1;
            endpoint2.joinNetwork(world, network);
        } else if(net1 != net2) {
            if(net1.size() >= net2.size()) {
                network = net1;
                network.merge(net2);
            } else {
                network = net2;
                network.merge(net1);
            }
        } else {
            network = net1;
        }

        return network;
    }

    @Nullable
    protected TransmissionLinePart tryGrabUnloadedPart(IWireEndpoint endpoint1, IWireEndpoint endpoint2, BaseWireEntity forEntity, PartId id) {
        // Try to resolve trees for correct merging of lines
        resolveTree(endpoint1);
        resolveTree(endpoint2);

        var existingPart = lineParts.get(id);
        if(existingPart != null) {
            existingPart.grab(forEntity, id);
            return existingPart;
        }

        return null;
    }

    private boolean makeTransmissionLine(TransmissionLinePart linePart) {
        if(linePart.getLine() != null) {
            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] makeTransmissionLine: part={} already has line={}",
                    System.identityHashCode(linePart),
                    linePart.getLine()
            );
            return true;
        }

        var endpoint1 = linePart.getEndpoint1();
        var endpoint2 = linePart.getEndpoint2();

        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] makeTransmissionLine: part={} endpoint1={} endpoint2={}",
                System.identityHashCode(linePart),
                endpoint1,
                endpoint2
        );

        if(endpoint1 == null || endpoint2 == null) {
            PowerGrid.LOGGER.warn(
                    "[TransmissionLineRepair] makeTransmissionLine FAILED: null endpoint part={} endpoint1={} endpoint2={}",
                    System.identityHashCode(linePart),
                    endpoint1,
                    endpoint2
            );
            return false;
        }

        var node1Before = endpoint1.getNode(world);
        var node2Before = endpoint2.getNode(world);
        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] makeTransmissionLine: nodes before prepare part={} node1={} node2={} same={}",
                System.identityHashCode(linePart),
                node1Before,
                node2Before,
                node1Before == node2Before
        );

        // This method needs to ensure proper ordering of segments in the transmission line.
        var network = prepareForConnection(endpoint1, endpoint2);
        if(network == null) {
            PowerGrid.LOGGER.warn(
                    "[TransmissionLineRepair] makeTransmissionLine FAILED: prepareForConnection returned null part={} node1={} node2={}",
                    System.identityHashCode(linePart),
                    node1Before,
                    node2Before
            );
            return false;
        }

        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] makeTransmissionLine: prepareForConnection SUCCESS part={} network={} ",
                System.identityHashCode(linePart),
                network
        );

        if(ModdedConfigs.logsEnabled())
            PowerGrid.LOGGER.debug("Creating a transmission line for {}", linePart);
        var node1 = endpoint1.getNode(world);
        var node2 = endpoint2.getNode(world);

        // Make sure nodes are up-to-date
        movePartMap(linePart.getNode1(), node1, linePart);
        linePart.setNode1(node1);
        movePartMap(linePart.getNode2(), node2, linePart);
        linePart.setNode2(node2);

        int nConns1 = connectionCount(endpoint1);
        int nConns2 = connectionCount(endpoint2);
        List<IElectricNode> nodes;
        var connected1 = nConns1 == 1 ? !(nodes = globalGraph.getConnectedNodes(node1)).isEmpty() ? nodes.get(0) : null : null;
        var connected2 = nConns2 == 1 ? !(nodes = globalGraph.getConnectedNodes(node2)).isEmpty() ? nodes.get(0) : null : null;

        TransmissionLine line1 = null, line2 = null;
        if(nConns1 == 1) {
            // We can attach to an existing line on endpoint1
            var wire = globalGraph.getFirstWire(node1, connected1);
            if(wire instanceof TransmissionLine curLine) {
                line1 = curLine;
            }
        }
        if(nConns2 == 1) {
            // We can attach to an existing line on endpoint2
            var wire = globalGraph.getFirstWire(node2, connected2);
            if(wire instanceof TransmissionLine curLine) {
                line2 = curLine;
                if(line1 != null) {
                    if(line1 != line2) {
                        linePart.setLine(line1);
                        // We can extend the first line by the second node.
                        if(ModdedConfigs.logsEnabled())
                            PowerGrid.LOGGER.debug("{}: Extending line at end by wire {}, terminating node is now {}", line1, linePart, node2);
                        if(line1.getNode2() != node1)
                            line1.flip();
                        line1.addLastSegment(linePart);
                        // We need to merge lines.
                        if(ModdedConfigs.logsEnabled())
                            PowerGrid.LOGGER.debug("{}: Merging transmission lines between {} and {}", line1, node1, node2);
                        if (curLine.getNode1() != line1.getNode2())
                            curLine.flip();
                        line1.merge(curLine);
                    } else {
                        // We are merging two ends of a single line, this cannot happen or things will break.
                        line2 = null;
                    }
                    line1 = null;
                } else {
                    linePart.setLine(line2);
                    if(ModdedConfigs.logsEnabled())
                        PowerGrid.LOGGER.debug("{}: Extending line at beginning by wire {}, starting node is now {}", line2, linePart, node1);
                    // We can extend this line by the first node.
                    if(line2.getNode1() != node2)
                        line2.flip();
                    line2.addFirstSegment(linePart);
                }
            }
        }
        if(line1 != null) {
            linePart.setLine(line1);
            // We can extend this line by the second node.
            if(ModdedConfigs.logsEnabled())
                PowerGrid.LOGGER.debug("{}: Extending line at end by wire {}, terminating node is now {}", line1, linePart, node2);
            if(line1.getNode2() != node1)
                line1.flip();
            line1.addLastSegment(linePart);
        }
        if(line1 == null && line2 == null) {
            var line = new TransmissionLine(linePart.getResistance(), endpoint1, endpoint2, this);
            linePart.setLine(line);
            line.segments.add(linePart);
            network.addWire(line);
            if(ModdedConfigs.logsEnabled())
                PowerGrid.LOGGER.debug("{}: New transmission line between {} and {}", line, node1, node2);
        }

        setDirty();
        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] makeTransmissionLine SUCCESS part={} line={} node1={} node2={}",
                System.identityHashCode(linePart),
                linePart.getLine(),
                linePart.getNode1(),
                linePart.getNode2()
        );
        return true;
    }

    @Nullable
    public ElectricWire makeSimpleWire(IWireEndpoint endpoint1, IWireEndpoint endpoint2, float resistance) {
        var network = prepareForConnection(endpoint1, endpoint2);
        if(network == null)
            return null;

        var node1 = endpoint1.getNode(world);
        var node2 = endpoint2.getNode(world);

        var wire = new ElectricWire(resistance, node1, node2);
        network.addWire(wire);

        setDirty();
        return wire;
    }

    @Nullable
    public ElectricWire makeTransmissionLine(IWireEndpoint endpoint1, IWireEndpoint endpoint2, BaseWireEntity forEntity, PartId id) {
        add(endpoint1);
        add(endpoint2);

        var linePart = tryGrabUnloadedPart(endpoint1, endpoint2, forEntity, id);
        if(linePart != null && linePart.getLine() != null)
            return linePart;

        if(linePart == null)
            linePart = TransmissionLinePart.uniquePart(forEntity.getResistance(), endpoint1, endpoint2, forEntity, this, id);

        if(makeTransmissionLine(linePart))
            return linePart;
        return null;
    }

    public List<TransmissionLinePart> findConnectedWires(ElectricBehaviour behaviour) {
        var wires = new ArrayList<TransmissionLinePart>();
        for(var node : behaviour.getExternalNodes()) {
            var parts = partNodeMap.get(node);
            if(parts == null)
                continue;
            wires.addAll(parts);
        }
        return wires;
    }

    public List<TransmissionLinePart> findConnectedWires(IWireEndpoint endpoint) {
        var parts = partNodeMap.get(endpoint.getNode(world));
        if(parts == null)
            return null;
        return List.copyOf(parts);
    }

    public void deferredRewire(Collection<TransmissionLinePart> wires) {
        deferredRewireEntities.addAll(wires);
    }

    public void deferredRewire(TransmissionLinePart part) {
        deferredRewireEntities.add(part);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var partList = new ListTag();
        for(var part : lineParts.values()) {
            partList.add(part.toNbt());
        }
        tag.put("Parts", partList);
        return tag;
    }

    protected void readNbt(CompoundTag nbt) {
        var partList = nbt.getList("Parts", Tag.TAG_COMPOUND);

        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] readNbt: Parts={} linePartsBefore={}",
                partList.size(),
                lineParts.size()
        );

        /*
         * TransmissionLine itself is runtime state and is not serialized.
         * The saved TransmissionLinePart objects therefore have to be
         * resolved again after the world has loaded.
         *
         * Do not wait for the wire entity to be grabbed: a server restart
         * can restore the SavedData before the corresponding entities/chunks
         * are available. Queue every restored part and let the normal repair
         * pass retry until both endpoints are available.
         */
        int restored = 0;
        int queued = 0;

        for(var entryGeneric : partList) {
            var partEntry = (CompoundTag) entryGeneric;
            var part = TransmissionLinePart.uniquePart(partEntry, this);
            restored++;

            if(part == null) {
                PowerGrid.LOGGER.warn(
                        "[TransmissionLineRepair] readNbt: uniquePart returned null for entry {}",
                        restored
                );
                continue;
            }

            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] RESTORED part={} endpoint1={} endpoint2={} line={} linePartsNow={}",
                    System.identityHashCode(part),
                    part.getEndpoint1(),
                    part.getEndpoint2(),
                    part.getLine(),
                    lineParts.size()
            );

            if(part.getLine() == null) {
                queueTransmissionLineRepair(part);
                queued++;
            } else {
                PowerGrid.LOGGER.info(
                        "[TransmissionLineRepair] part={} already has line={}, no repair needed",
                        System.identityHashCode(part),
                        part.getLine()
                );
            }
        }

        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] readNbt complete: restored={} queued={} pending={}",
                restored,
                queued,
                pendingTransmissionLineRepair.size()
        );
    }

    public void nodeHolderUnloaded(@NotNull OwnedFloatingNode ownedNode) {
        // Here, we need to choose between preserving transmission line junction nodes or removing them.
        // A transmission line should be preserved if terminates on a junction which connects to more
        // transmission lines.
        Collection<TransmissionLine> lines;
        while(true) {
            lines = globalGraph.getConnectedLines(ownedNode);
            if(lines.size() != 1 || globalGraph.connectionCount(ownedNode) != 1) {
                // We CANNOT delete the node, as it still is part of a bigger circuit.
                break;
            }
            // No need to keep this line (or the node).
            var line = lines.iterator().next();
            var removeNode = ownedNode;
            if(line.getNode1() == ownedNode) {
                ownedNode = line.getNode2();
            } else {
                assert line.getNode2() == ownedNode;
                ownedNode = line.getNode1();
            }
            // Also, we need to inform the wire entities about this.
            line.unresolve();
            setDirty();
            scheduleIslandDiscovery(removeNode.getNetwork());
            removeNode.remove();
            globalExternalNodes.remove(removeNode.endpoint);
        }
    }

    public void nodeHolderRemoved(@NotNull OwnedFloatingNode ownedNode) {
        // Connections should already be broken by endpoint removal stuff.
        var parts = partNodeMap.remove(ownedNode);
        if(parts != null && !parts.isEmpty()) {
            // But just in case, remove any part that still exists.
            for(var part : parts) {
                if(part.owner != null) {
                    part.owner.kill();
                } else {
                    part.remove();
                }
            }
        }
        scheduleIslandDiscovery(ownedNode.getNetwork());
        ownedNode.remove();
        globalExternalNodes.remove(ownedNode.endpoint);
    }

    private boolean traceTree(IWireEndpoint endpoint, Set<IWireEndpoint> visited) {
        if(!visited.add(endpoint))
            return false;
        Collection<TransmissionLinePart> parts = partNodeMap.get(globalExternalNodes.get(endpoint));
        if(parts == null)
            return false;
        // Make sure endpoints are always up to date in line parts.
        // TODO: Verify that this doesn't brake a bunch of things
        addAndMigrateNode(endpoint);
        parts = List.copyOf(parts);
        boolean continueResolving = false;
        for(var part : parts) {
            if(part.getLine() != null) {
                // This branch connects to a valid line, back-trace and resolve all line parts.
                continueResolving = true;
            }
            if(parts.size() == 1) {
                if(ModdedConfigs.logsEnabled())
                    PowerGrid.LOGGER.debug("Found edge line at {}", endpoint);
                if(part.getEndpoint1().equals(endpoint)) {
                    // Check endpoint2
                    if(part.getEndpoint2().isValid(world)) {
                        // Resolve segment
                        makeTransmissionLine(part);
                        return true;
                    }
                } else if(part.getEndpoint2().equals(endpoint)) {
                    // Check endpoint1
                    if(part.getEndpoint1().isValid(world)) {
                        // Resolve segment
                        makeTransmissionLine(part);
                        return true;
                    }
                } else {
                    PowerGrid.LOGGER.warn("[1] Part was expected to have this endpoint");
                }
                break;
            }
            if(ModdedConfigs.logsEnabled())
                PowerGrid.LOGGER.debug("Continuing line trace through {}", endpoint);
            if(part.getEndpoint1().equals(endpoint)) {
                if(traceTree(part.getEndpoint2(), visited)) {
                    makeTransmissionLine(part);
                    continueResolving = true;
                }
            } else if(part.getEndpoint2().equals(endpoint)) {
                if(traceTree(part.getEndpoint1(), visited)) {
                    makeTransmissionLine(part);
                    continueResolving = true;
                }
            } else {
                PowerGrid.LOGGER.warn("[2] Part was expected to have this endpoint");
            }
        }
        return continueResolving;
    }

    private void resolveTree(@NotNull IWireEndpoint endpoint) {
        var unresolvedLines = partNodeMap.get(globalExternalNodes.get(endpoint));
        if(unresolvedLines == null)
            return;
        // We need to trace the graph to all terminating nodes and see if any are loaded,
        // if so, we need to resolve all lines between them to ensure correct unloaded chunk behaviour.
        var visited = new HashSet<IWireEndpoint>();
        if(ModdedConfigs.logsEnabled())
            PowerGrid.LOGGER.debug("Starting line trace at {}", endpoint);
        traceTree(endpoint, visited);
    }

    public void addAndMigrateNode(IWireEndpoint endpoint) {
        var newNode = endpoint.getNode(world);
        if(newNode == null)
            return;
        addAndMigrateNode(newNode);
    }

    public void addAndMigrateNode(OwnedFloatingNode newNode) {
        var endpoint = newNode.endpoint;
        var oldNode = globalExternalNodes.put(endpoint, newNode);
        addAndMigrateNode(oldNode, newNode);
    }

    public void addAndMigrateNode(IWireEndpoint oldEndpoint, OwnedFloatingNode newNode) {
        if(newNode == null)
            return;
        var endpoint = newNode.endpoint;
        var oldNode = globalExternalNodes.put(endpoint, newNode);
        addAndMigrateNode(oldNode, newNode);
        var oldNode2 = globalExternalNodes.remove(oldEndpoint);
        addAndMigrateNode(oldNode2, newNode);

        var line = findLineMiddle(newNode);
        if(line != null) {
            line.splitAt(newNode);
        }
    }

    public void addAndMigrateNode(
            OwnedFloatingNode oldNode,
            OwnedFloatingNode newNode
    ) {
        if(newNode == null)
            return;

        if(oldNode == newNode)
            return;

        var endpoint = newNode.endpoint;

        /*
         * The new node must always be registered before any transmission-line
         * endpoint is moved to it.
         *
         * This is especially important after world reload because the
         * OwnedFloatingNode object may have been recreated while the graph
         * still contains the old object or no object at all.
         */
        globalGraph.addNode(newNode);

        if(ModdedConfigs.logsEnabled()) {
            PowerGrid.LOGGER.debug(
                    "Migrating external node from {} to {}",
                    oldNode,
                    newNode
            );
        }

        /*
         * ------------------------------------------------------------
         * 1. Migrate TransmissionLinePart bookkeeping.
         * ------------------------------------------------------------
         */
        if(oldNode != null) {
            var parts = partNodeMap.remove(oldNode);

            if(parts != null) {
                for(var part : List.copyOf(parts)) {
                    if(part == null)
                        continue;

                    boolean endpoint1 =
                            part.getEndpoint1() != null &&
                                    part.getEndpoint1().equals(endpoint);

                    boolean endpoint2 =
                            part.getEndpoint2() != null &&
                                    part.getEndpoint2().equals(endpoint);

                    /*
                     * The node object can be stale while the endpoint itself
                     * is still identical. Endpoint equality therefore has
                     * priority over object identity here.
                     */
                    if(endpoint1 || part.getNode1() == oldNode) {
                        part.setNode1(newNode);

                        if(ModdedConfigs.logsEnabled()) {
                            PowerGrid.LOGGER.debug(
                                    "Migrated part node1: {} -> {} for {}",
                                    oldNode,
                                    newNode,
                                    part
                            );
                        }
                    }

                    if(endpoint2 || part.getNode2() == oldNode) {
                        part.setNode2(newNode);

                        if(ModdedConfigs.logsEnabled()) {
                            PowerGrid.LOGGER.debug(
                                    "Migrated part node2: {} -> {} for {}",
                                    oldNode,
                                    newNode,
                                    part
                            );
                        }
                    }

                    partNodeMap
                            .computeIfAbsent(
                                    newNode,
                                    $ -> new HashSet<>()
                            )
                            .add(part);
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * 2. Migrate the electrical network.
         * ------------------------------------------------------------
         *
         * oldNode can legitimately be null.
         *
         * This is the exact situation which previously caused:
         *
         * Cannot invoke "OwnedFloatingNode.getNetwork()"
         * because "oldNode" is null
         */
        ElectricalNetwork unified = null;

        if(oldNode != null && oldNode.getNetwork() != null) {
            var oldNetwork = oldNode.getNetwork();

            inNetwork(oldNetwork, newNode);

            /*
             * inNetwork() may merge networks. Re-read the network after
             * that operation instead of assuming the original object is
             * still the active network.
             */
            unified = newNode.getNetwork();

            if(unified == null)
                unified = oldNetwork;
        } else if(newNode.getNetwork() != null) {
            unified = newNode.getNetwork();
        }

        /*
         * ------------------------------------------------------------
         * 3. Migrate graph connections.
         * ------------------------------------------------------------
         *
         * Never use:
         *
         *   globalGraph.disconnect(...)
         *   globalGraph.connect(...)
         *
         * here.
         *
         * Those methods invoke lineDisconnected()/lineConnected(), which
         * can modify transmissionLines, split lines and schedule island
         * discovery while this migration is still incomplete.
         */
        if(oldNode != null) {
            var connectedLines =
                    List.copyOf(
                            globalGraph.getConnectedLines(oldNode)
                    );

            for(var line : connectedLines) {
                if(line == null)
                    continue;

                if(line.getNode1() == oldNode) {
                    migrateTransmissionLineEndpoint(
                            oldNode,
                            newNode,
                            line,
                            true
                    );

                } else if(line.getNode2() == oldNode) {
                    migrateTransmissionLineEndpoint(
                            oldNode,
                            newNode,
                            line,
                            false
                    );

                } else {
                    /*
                     * The graph still contained the line, but the line itself
                     * no longer references oldNode. This is stale bookkeeping.
                     */
                    PowerGrid.LOGGER.warn(
                            "[PowerDebug] Stale graph connection during node migration: " +
                                    "line={} oldNode={} newNode={}",
                            line,
                            oldNode,
                            newNode
                    );
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * 4. Recover TransmissionLinePart references which were already
         *    connected to a TransmissionLine.
         * ------------------------------------------------------------
         *
         * This handles the case where partNodeMap and the graph were
         * reconstructed in different orders during world loading.
         */
        var newParts = partNodeMap.get(newNode);

        if(newParts != null) {
            for(var part : List.copyOf(newParts)) {
                if(part == null)
                    continue;

                var line = part.getLine();

                if(line == null)
                    continue;

                if(
                        line.getNode1() == oldNode &&
                                oldNode != null
                ) {
                    migrateTransmissionLineEndpoint(
                            oldNode,
                            newNode,
                            line,
                            true
                    );

                } else if(
                        line.getNode2() == oldNode &&
                                oldNode != null
                ) {
                    migrateTransmissionLineEndpoint(
                            oldNode,
                            newNode,
                            line,
                            false
                    );
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * 5. Remove the old graph node only after every connection has
         *    been migrated.
         * ------------------------------------------------------------
         */
        if(oldNode != null) {
            if(globalGraph.getConnectedLines(oldNode).isEmpty()) {
                globalGraph.removeNode(oldNode);
            } else {
                PowerGrid.LOGGER.warn(
                        "[PowerDebug] Old node still has graph connections after migration: " +
                                "oldNode={} newNode={} lines={}",
                        oldNode,
                        newNode,
                        globalGraph.getConnectedLines(oldNode).size()
                );
            }

            /*
             * Do not call oldNode.getNetwork() again here unless it is known
             * to be non-null. The node may already have been removed from its
             * network during a merge.
             */
            if(unified != null && oldNode.getNetwork() == unified) {
                unified.removeNode(oldNode);
            }
        }

        /*
         * ------------------------------------------------------------
         * 6. Final consistency check.
         * ------------------------------------------------------------
         */
        var finalParts = partNodeMap.get(newNode);

        if(finalParts != null) {
            for(var part : finalParts) {
                if(part == null)
                    continue;

                var line = part.getLine();

                if(line == null)
                    continue;

                if(line.getNode1() == newNode ||
                        line.getNode2() == newNode) {

                    if(ModdedConfigs.logsEnabled()) {
                        PowerGrid.LOGGER.debug(
                                "[PowerDebug] Node migration verified: " +
                                        "part={} line={} newNode={}",
                                System.identityHashCode(part),
                                line,
                                newNode
                        );
                    }
                }
            }
        }
    }

    /**
     * Moves one TransmissionLine endpoint from an old node to its replacement
     * without invoking NetworkGraph disconnect/connect hooks.
     *
     * This method is used exclusively for node restoration/migration.
     */
    private void migrateTransmissionLineEndpoint(
            OwnedFloatingNode oldNode,
            OwnedFloatingNode newNode,
            TransmissionLine line,
            boolean firstEndpoint
    ) {
        if(line == null || newNode == null)
            return;

        if(oldNode == null)
            return;

        if(firstEndpoint) {
            if(line.getNode1() != oldNode)
                return;
        } else {
            if(line.getNode2() != oldNode)
                return;
        }

        /*
         * Make sure the replacement node exists in the graph.
         */
        globalGraph.addNode(newNode);

        /*
         * Make sure the opposite endpoint exists as well.
         */
        var otherNode =
                firstEndpoint
                        ? line.getNode2()
                        : line.getNode1();

        if(otherNode != null)
            globalGraph.addNode(otherNode);

        /*
         * Move the graph bookkeeping without invoking graph hooks.
         */
        boolean migrated =
                globalGraph.migrateWireEndpoint(
                        oldNode,
                        newNode,
                        line
                );

        if(!migrated) {
            PowerGrid.LOGGER.warn(
                    "[PowerDebug] Failed to migrate graph endpoint: " +
                            "line={} oldNode={} newNode={} firstEndpoint={}",
                    line,
                    oldNode,
                    newNode,
                    firstEndpoint
            );
            return;
        }

        /*
         * Only change the TransmissionLine object after its graph
         * bookkeeping has been successfully migrated.
         */
        if(firstEndpoint) {
            line.setNode1(newNode);
        } else {
            line.setNode2(newNode);
        }

        if(ModdedConfigs.logsEnabled()) {
            var other =
                    firstEndpoint
                            ? line.getNode2()
                            : line.getNode1();

            PowerGrid.LOGGER.debug(
                    "[PowerDebug] MIGRATED LINE STATE: " +
                            "line={} " +
                            "oldNode={} " +
                            "newNode={} " +
                            "otherNode={} " +
                            "newNodeVoltage={}V " +
                            "otherNodeVoltage={}V " +
                            "lineResistance={}Ohm " +
                            "lineNetwork={} " +
                            "lineNetworkConverged={}",
                    line,
                    oldNode,
                    newNode,
                    other,
                    newNode.getVoltage(),
                    other == null ? 0 : other.getVoltage(),
                    line.getResistance(),
                    line.getNetwork() == null
                            ? "null"
                            : System.identityHashCode(
                            line.getNetwork()
                    ),
                    line.getNetwork() != null &&
                            line.getNetwork().isConverged()
            );
        }
    }

    public void nodeHolderAdded(@NotNull OwnedFloatingNode ownedNode, boolean hasInternals) {
        if(ModdedConfigs.logsEnabled())
            PowerGrid.LOGGER.debug("Node holder added, {}", ownedNode);
        addAndMigrateNode(ownedNode.endpoint);
        // Try to resolve an end of a transmission line
        resolveTree(ownedNode.endpoint);
        if(hasInternals) {
            var line = findLineMiddle(ownedNode);
            if(line != null) {
                line.splitAt(ownedNode);
            }
        }
    }

    // TODO: When pausing we can simplify transmission lines.
    public void prepareUnpaused(OwnedFloatingNode node) {
        if(ModdedConfigs.logsEnabled())
            PowerGrid.LOGGER.debug("Preparing node for unpaused internal connections");
        var line = findLineMiddle(node);
        if(line != null)
            line.splitAt(node);
    }

    /**
     * Save an entity to be recovered into the transmission line once its chunk is loaded back into the world.
     *
     * @param entityId       Transmission line part entity id
     * @param lastKnownChunk Last known chunk of the entity
     */
    public void bounty(PartId entityId, ChunkPos lastKnownChunk) {
        if(world.hasChunk(lastKnownChunk.x, lastKnownChunk.z)) {
            checkForExistence.computeIfAbsent(lastKnownChunk, $ -> new CheckChunk()).add(entityId);
            return;
        }
        expectedInChunks.computeIfAbsent(lastKnownChunk, $ -> new CheckChunk()).add(entityId);
    }

    public void tracking(ServerPlayer tracker, IWireEndpoint endpoint, boolean end) {
        if(!end) {
            trackers.computeIfAbsent(endpoint, $ -> new HashSet<>()).add(tracker);
//            var lines = globalGraph.getConnectedLines(endpoint.getNode(world));
//            ModdedPackets.sendToClient(new TransmissionLineManagementS2CPacket(endpoint, lines), tracker);
        } else {
            var list = trackers.get(endpoint);
            if(list == null)
                return;
            list.remove(tracker);
            if(list.isEmpty())
                trackers.remove(endpoint);
        }
    }

    @NotNull
    public Set<ServerPlayer> getTrackers(IWireEndpoint endpoint) {
        var set = trackers.get(endpoint);
        if(set == null)
            return Set.of();
        return set;
    }

    public void dropTrackers(ServerPlayer player) {
        var iter = trackers.entrySet().iterator();
        while(iter.hasNext()) {
            var entry = iter.next();
            entry.getValue().remove(player);
            if(entry.getValue().isEmpty())
                iter.remove();
        }
    }

    public void chunkLoaded(ChunkPos chunkPos) {
        var set = expectedInChunks.remove(chunkPos);
        if(set != null) {
            if(checkForExistence.containsKey(chunkPos)) {
                checkForExistence.get(chunkPos).addAll(set.entities);
            } else {
                checkForExistence.put(chunkPos, set);
            }
        }
    }

    public OwnedFloatingNode holderOrPlaceholderNode(@NotNull IWireEndpoint endpoint) {
        var node = globalExternalNodes.get(endpoint);
        if (node != null)
            return node;
        if(endpoint.isValid(world)) {
            node = endpoint.getNode(world);
        } else {
            node = new OwnedFloatingNode(endpoint);
        }
        globalExternalNodes.put(endpoint, node);
        return node;
    }

    public void registerPart(PartId persistentOwnerId, TransmissionLinePart part) {
        lineParts.put(persistentOwnerId, part);
        partNodeMap.computeIfAbsent(part.getNode1(), $ -> new HashSet<>()).add(part);
        partNodeMap.computeIfAbsent(part.getNode2(), $ -> new HashSet<>()).add(part);
        setDirty();
    }

    public void unregisterPart(PartId persistentOwnerId, TransmissionLinePart part) {
        lineParts.remove(persistentOwnerId);
        var set = partNodeMap.get(part.getNode1());
        if(set != null) {
            set.remove(part);
            if(set.isEmpty())
                partNodeMap.remove(part.getNode1());
        }
        set = partNodeMap.get(part.getNode2());
        if(set != null) {
            set.remove(part);
            if(set.isEmpty())
                partNodeMap.remove(part.getNode2());
        }
        setDirty();
    }

    @Nullable
    public TransmissionLinePart getPart(PartId persistentOwnerId) {
        return lineParts.get(persistentOwnerId);
    }

    public void inNetwork(@Nullable ElectricalNetwork network, @NotNull OwnedFloatingNode node) {
        if(network == null)
            return;
        if(node.getNetwork() != null) {
            if(node.getNetwork() != network) {
                network.merge(node.getNetwork());
            }
        } else {
            node.endpoint.joinNetwork(world, network);
        }
    }

    public void movePartMap(OwnedFloatingNode oldNode, OwnedFloatingNode newNode, TransmissionLinePart part) {
        if(oldNode == newNode || oldNode == null || newNode == null)
            return;
        var parts = partNodeMap.get(oldNode);
        if(parts == null)
            return;
        if(parts.remove(part)) {
            if (parts.isEmpty())
                partNodeMap.remove(oldNode);
            partNodeMap.computeIfAbsent(newNode, $ -> new HashSet<>()).add(part);
        }
    }

    public void removeFromNetwork(OwnedFloatingNode node) {
        var checked = new HashSet<IElectricNode>();
        var toCheck = new ArrayList<IElectricNode>();
        toCheck.add(node);
        while(!toCheck.isEmpty()) {
            var check = toCheck.remove(0);
            if(!checked.add(check))
                continue;
            var nodes = globalGraph.getConnectedNodes(check);
            var couplings = globalGraph.getCouplings(node);
            couplings.forEach(INode::remove);
            for(var connected : nodes) {
                var wires = List.copyOf(globalGraph.getWires(node, connected));
                for(var wire : wires)
                    wire.remove();
                toCheck.add(connected);
            }
            node.remove();
        }
    }

    private static class CheckChunk {
        public final Set<PartId> entities = Sets.newConcurrentHashSet();
        public int ticks = 0;

        public void add(PartId id) {
            entities.add(id);
            ticks = 0;
        }

        public void addAll(Set<PartId> ids) {
            entities.addAll(ids);
            ticks = 0;
        }
    }

    public interface PartId {
        BaseWireEntity getEntity(ServerLevel level);
    }

    public record SimpleId(UUID id) implements PartId {
        @Override
        public BaseWireEntity getEntity(ServerLevel level) {
            var entity = level.getEntity(id);
            if(entity instanceof BaseWireEntity wire)
                return wire;
            return null;
        }
    }

    public record ComplexId(UUID id, int sub) implements PartId {
        @Override
        public BaseWireEntity getEntity(ServerLevel level) {
            var entity = level.getEntity(id);
            if(entity instanceof BaseWireEntity wire)
                return wire;
            return null;
        }
    }

    public record CouplingKey(Island island1, Island island2) {
        @Override
        public boolean equals(Object obj) {
            if(obj == this)
                return true;
            if(obj instanceof CouplingKey other) {
                return (island1 == other.island1 && island2 == other.island2) ||
                        (island1 == other.island2 && island2 == other.island1);
            }
            return false;
        }

        @Override
        public int hashCode() {
            // Symmetric hash code
            return Objects.hashCode(island1) + Objects.hashCode(island2);
        }

        public boolean has(Island island) {
            return island == island1 || island == island2;
        }

        public static CouplingKey of(Island island1, Island island2) {
            return new CouplingKey(island1, island2);
        }

        public Island other(Island island) {
            if(island == island1)
                return island2;
            if(island == island2)
                return island1;
            return null;
        }
    }

    public static class Island {
        private final Set<INetworkElement> elements = new HashSet<>();
        private final Map<CouplingKey, Set<TransmissionLine>> couplings;

        public Island(Map<CouplingKey, Set<TransmissionLine>> couplings) {
            this.couplings = couplings;
        }

        public void addAll(Island island) {
            elements.addAll(island.elements);
            var merged = couplings.remove(CouplingKey.of(this, island));
            if(merged != null) {
                elements.addAll(merged);
            }

            var newlyAdded = new HashMap<CouplingKey, Set<TransmissionLine>>();
            couplings.entrySet().removeIf(entry -> {
                if(entry.getKey().has(island)) {
                    var key = CouplingKey.of(this, entry.getKey().other(island));
                    if(couplings.containsKey(key))  {
                        couplings.get(key).addAll(entry.getValue());
                    } else {
                        // Avoid concurrent modification
                        newlyAdded.put(key, entry.getValue());
                    }
                    return true;
                }
                return false;
            });
            couplings.putAll(newlyAdded);
        }

        public void add(INetworkElement element) {
            elements.add(element);
        }

        public boolean contains(INetworkElement element) {
            return elements.contains(element);
        }

        public void addCoupling(Island connectedIsland, TransmissionLine line) {
            couplings.computeIfAbsent(CouplingKey.of(this, connectedIsland), $ -> new HashSet<>())
                    .add(line);
        }
    }

    private void repairPendingTransmissionLines() {
        if (pendingTransmissionLineRepair.isEmpty()) {
            return;
        }

        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] repair pass START pending={}",
                pendingTransmissionLineRepair.size()
        );

        var iterator = pendingTransmissionLineRepair.iterator();

        while (iterator.hasNext()) {
            var part = iterator.next();

            if (part == null) {
                PowerGrid.LOGGER.warn(
                        "[TransmissionLineRepair] NULL part found in pending set; removing"
                );
                iterator.remove();
                continue;
            }

            int attempt = transmissionLineRepairAttempts.merge(part, 1, Integer::sum);

            PowerGrid.LOGGER.info(
                    "[TransmissionLineRepair] ATTEMPT #{} part={} pending={} endpoint1={} endpoint2={} line={}",
                    attempt,
                    System.identityHashCode(part),
                    pendingTransmissionLineRepair.size(),
                    part.getEndpoint1(),
                    part.getEndpoint2(),
                    part.getLine()
            );

            if (part.getLine() != null) {
                PowerGrid.LOGGER.info(
                        "[TransmissionLineRepair] part={} already repaired before attempt; removing from queue",
                        System.identityHashCode(part)
                );
                iterator.remove();
                transmissionLineRepairAttempts.remove(part);
                continue;
            }

            try {
                part.refreshEndpointNodes();

                PowerGrid.LOGGER.info(
                        "[TransmissionLineRepair] part={} after refresh: endpoint1={} endpoint2={} node1={} node2={} line={}",
                        System.identityHashCode(part),
                        part.getEndpoint1(),
                        part.getEndpoint2(),
                        part.getNode1(),
                        part.getNode2(),
                        part.getLine()
                );

                if (part.getLine() != null) {
                    PowerGrid.LOGGER.info(
                            "[TransmissionLineRepair] part={} refreshEndpointNodes() repaired the line",
                            System.identityHashCode(part)
                    );
                    iterator.remove();
                    transmissionLineRepairAttempts.remove(part);
                    continue;
                }

                var endpoint1 = part.getEndpoint1();
                var endpoint2 = part.getEndpoint2();

                if (endpoint1 == null || endpoint2 == null) {
                    PowerGrid.LOGGER.warn(
                            "[TransmissionLineRepair] WAIT part={} because endpoint is null: endpoint1={} endpoint2={}",
                            System.identityHashCode(part),
                            endpoint1,
                            endpoint2
                    );
                    continue;
                }

                var node1 = endpoint1.getNode(world);
                var node2 = endpoint2.getNode(world);

                PowerGrid.LOGGER.info(
                        "[TransmissionLineRepair] part={} resolved nodes: node1={} node2={} same={}",
                        System.identityHashCode(part),
                        node1,
                        node2,
                        node1 == node2
                );

                if (node1 == null || node2 == null) {
                    PowerGrid.LOGGER.warn(
                            "[TransmissionLineRepair] WAIT part={} because node is null: node1={} node2={}",
                            System.identityHashCode(part),
                            node1,
                            node2
                    );
                    continue;
                }

                boolean repaired = makeTransmissionLine(part);

                PowerGrid.LOGGER.info(
                        "[TransmissionLineRepair] makeTransmissionLine result: part={} success={} line={}",
                        System.identityHashCode(part),
                        repaired,
                        part.getLine()
                );

                if (repaired) {
                    iterator.remove();
                    transmissionLineRepairAttempts.remove(part);
                    PowerGrid.LOGGER.info(
                            "[TransmissionLineRepair] COMPLETE part={} remainingPending={}",
                            System.identityHashCode(part),
                            pendingTransmissionLineRepair.size()
                    );
                }
            } catch (Exception e) {
                PowerGrid.LOGGER.error(
                        "[TransmissionLineRepair] EXCEPTION repairing part={} attempt={}; will retry",
                        System.identityHashCode(part),
                        attempt,
                        e
                );
            }
        }

        PowerGrid.LOGGER.info(
                "[TransmissionLineRepair] repair pass END pending={}",
                pendingTransmissionLineRepair.size()
        );
    }

}
