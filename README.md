# Kiln
Gradle plugin to aid in the development of shards.

## Roadmap
 - [x] Downloading Minecraft
   - [x] Libraries
   - [x] Natives
 - [x] Deobfuscating Minecraft
   - [x] Yarn Mappings (Mostly)
   - [x] MCP Mappings (Pre-1.13)
   - [x] Mojang Mappings
   - [ ] Sand Mappings? (Custom GlassMC Mappings)
 - [x] Depending On Shards
   - [x] Shard Dependencies

## Testing
The current best way to test kiln is to run the **publishToMavenLocal** gradle plugin.
This publishes kiln to a local maven repository on your computer. After that you can use it as a dependency in a test shard.
