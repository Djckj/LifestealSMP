# LifestealSMP — Paper 26.2 source project

This is a Java source project targeting Paper 26.2 / Minecraft Java 26.2.

## Requirements
- Paper 26.2
- Java/JDK 25
- Maven 3.9 or newer

## Build on GitHub (no local Java/Maven needed)
1. Create a new GitHub repository.
2. Upload all files and folders from this project, including the hidden `.github` folder.
3. Open the repository's **Actions** tab and select **Build LifestealSMP**.
4. If prompted, enable Actions, then run the workflow with **Run workflow**, or push a commit to trigger it.
5. Open the completed run and download **LifestealSMP-plugin** from the Artifacts section.

The workflow uses Java 25 and Maven to compile the project and upload the JAR as a downloadable artifact.

## Build locally
Open a terminal in this folder and run:

```sh
mvn clean package
```

The resulting JAR should be at `target/lifesteal-smp-1.0.0.jar`. Copy it into your server's `plugins/` folder and restart the server.

## Commands
- `/hearts`
- `/lifesteal give <player> <lifestealer|swordaxe|armorbreaker>` (OP)
- `/lifesteal sethearts <player> <amount>` (OP)
- `/lifesteal recipe` (OP)

## Included
- Starts players at 10 hearts; heart totals persist in `plugins/LifestealSMP/config.yml`.
- Player kills transfer 1 heart, or 3 if the killer is holding the Lifestealer Axe.
- Maximum hearts default to 20. Players reaching 0 hearts are banned by default.
- Three shaped recipes and persistent one-time recipe locks.
- Lifestealer Axe is unbreakable.
- The Sword Axe has sword-like attack speed and a best-effort anvil enchantment transfer for sword enchantments.
- Armor Breaker adds two extra durability damage to each worn armor piece per successful player hit (approximately 3x for an ordinary armor-wearing hit).
- Special weapons cannot be placed in containers, shulker boxes, or moved by hoppers. Dropped weapons do not despawn or burn in common fire/lava/explosion/void damage paths.

## Compatibility / validation status
PaperMC's current documentation says Paper 26.2 uses Java 25 and `api-version: '26.2'`. This project targets `paper-api:26.2.build.129-stable` and Java 25. I could not compile or run integration tests in this environment because it only has Java 21 and Maven is not installed. Treat this as a source build requiring compilation and live server testing before production use. Make a world backup first.
