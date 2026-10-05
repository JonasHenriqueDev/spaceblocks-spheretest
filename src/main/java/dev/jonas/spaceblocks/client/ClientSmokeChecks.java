package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Uses the game's own screenshot API to inspect the development client. */
public final class ClientSmokeChecks {
    private static int ticks;
    private static boolean entered;
    private static boolean viewed;
    private static boolean captured;
    private ClientSmokeChecks() {}

    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("spaceblocks.smokeClient")) return;
        if (Boolean.getBoolean("spaceblocks.radialSmoke")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) return;
        ticks++;
        if (!entered && ticks > 80) {
            command(minecraft, "space enter");
            entered = true;
        }
        if (entered && !viewed && ticks > 180 && SpaceBlocks.isSpace(minecraft.level.dimension())) {
            command(minecraft, "space view");
            viewed = true;
        }
        if (viewed && !captured && ticks > 500) {
            Screenshot.grab(minecraft.gameDirectory, "spaceblocks-distant.png", minecraft.getMainRenderTarget(),
                    message -> SpaceBlocks.LOGGER.info("SPACEBLOCKS_CLIENT_SCREENSHOT: {}", message.getString()));
            captured = true;
            command(minecraft, "space surface");
        }
        if (captured && ticks == 700) {
            Screenshot.grab(minecraft.gameDirectory, "spaceblocks-surface.png", minecraft.getMainRenderTarget(),
                    message -> SpaceBlocks.LOGGER.info("SPACEBLOCKS_CLIENT_SURFACE_SCREENSHOT: {}", message.getString()));
        }
        if (captured && ticks == 730) command(minecraft, "space leave");
        if (captured && ticks > 800) minecraft.stop();
        if (!captured && ticks > 1200) {
            SpaceBlocks.LOGGER.error("SPACEBLOCKS_CLIENT_SMOKE_FAILED: dimension entry or render timed out");
            minecraft.stop();
        }
    }

    private static void command(Minecraft minecraft, String command) {
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        if (server == null) return;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player != null) server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
        });
    }
}
