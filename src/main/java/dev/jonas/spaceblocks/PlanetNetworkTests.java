package dev.jonas.spaceblocks;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Restricted to the developer's explicit loopback test run, never enabled in a normal install. */
public final class PlanetNetworkTests {
  public static void joined(PlayerEvent.PlayerLoggedInEvent e) {
    if (Boolean.getBoolean("spaceblocks.testNetworkServer")
        && e.getEntity() instanceof ServerPlayer p) {
      p.server.getPlayerList().op(p.getGameProfile());
      p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }
  }
}
