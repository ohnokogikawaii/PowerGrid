# About This Fork

This repository is a personal fork and modification of **Create: Power Grid** by **patryk3211**.

The purpose of this fork is to port the **Minecraft 1.20.1 version of Create: Power Grid, including modifications developed in my separate 1.20.1 repository, to Minecraft 1.21.1 with NeoForge**.

The 1.20.1 version containing my previous modifications can be found in my separate repository:

**https://github.com/ohnokogikawaii/Chat-GPT_powergrid**

## Important Notice

This is a **personal, independently maintained fork**. It is currently under active development and may contain bugs, insufficient testing, incomplete debugging, missing translations, or other issues.

Some features may not work as intended, and compatibility with other mods has not necessarily been tested.

If you find a bug, have a suggestion, notice a translation problem, or have an idea for improving the code, please feel free to send advice or feedback through the **GitHub contact email associated with this repository**.

I am still relatively new to Java and Minecraft mod development. Therefore, **constructive advice, code reviews, technical explanations, and suggestions for better implementation are very welcome**.

## Use of AI

**ChatGPT and other AI-assisted development tools are used during the development of this fork.**

AI is used for tasks such as understanding existing code, investigating errors, proposing implementations, refactoring, and assisting with the porting process.

AI-generated suggestions are reviewed, modified, tested, and integrated as appropriate. However, because I am still learning Java and Minecraft mod development, some code may contain mistakes or inefficient implementations.

If you notice something that could be improved, technical feedback is welcome.

## Development Status

This fork should be considered **experimental and under development**.

Features, internal implementations, APIs, and configuration may change without notice while the 1.21.1 NeoForge port is being developed.

Please report reproducible bugs with as much information as possible, such as:

* Minecraft version
* NeoForge version
* Create version
* Power Grid version
* Other installed mods
* Steps to reproduce the problem
* Crash logs or relevant log output
* Screenshots or videos when useful

Thank you for helping improve the project.


<p align="center">
    <img src="./src/main/resources/assets/powergrid/icon.png" alt="Logo" width="200">
</p>
<h1 align="center">Create: Power Grid</h1>

<p>Create: Power Grid is a mod that adds physics-based electricity simulation to the Create mod.</p>
<p>
Just like in the main mod, everything is designed to encourage creativity while introducing new challenges and obstacles.
In the end you will be the proud owner of a world-wide power grid - one that <i>hopefully</i> doesn't collapse after you
flip that one unmarked switch...
</p>
<p>
Create's in-game 'Ponder' documentation will walk you through the basic physics behind the mod and help you get started
with concepts like Ohm's law.
</p>

<p>
You can join the <a href="https://discord.gg/QQqqEnJqGz">Community Discord Server</a> if you want to discuss this mod or ask questions.
</p>

## Contributing
Reporting a bug? Make sure to test it with the latest version available. Describe the steps it takes to reproduce it and include anything that can help with resolving it (screenshots, videos, logs)

## Building
To build the mod from source you need to run either `:forge:build` or `fabric:build` gradle task, your mod jar will be located in `forge/build/libs` or `fabric/build/libs`.
If you want to also build the native acceleration binary you have to have **cmake** and a working **C++ compiler** installed. Configure the cmake executable location in `native/gradle.properties`. 
