package dev.jonas.spaceblocks;

import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in development check; inactive in normal Modrinth installs. */
public final class SmokeChecks {
    private SmokeChecks() {}

    public static void serverStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("spaceblocks.smokeServer")) return;
        try {
            int result = SpaceCommands.verify(event.getServer().createCommandSourceStack());
            if (result != 1) throw new IllegalStateException("Planet generation verification failed");
            Files.writeString(Path.of("smoke-server-result.txt"), "PASS: custom dimension, generator codec, solid core, six axial regions and exterior vacuum.\n");
            SpaceBlocks.LOGGER.info("SPACEBLOCKS_SMOKE_SERVER_OK");
        } catch (Exception exception) {
            SpaceBlocks.LOGGER.error("SPACEBLOCKS_SMOKE_SERVER_FAILED", exception);
            throw new IllegalStateException("Space Blocks smoke check failed", exception);
        } finally {
            event.getServer().halt(false);
        }
    }
}
