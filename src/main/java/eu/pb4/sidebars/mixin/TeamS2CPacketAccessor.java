package eu.pb4.sidebars.mixin;

import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(TeamS2CPacket.class)
public interface TeamS2CPacketAccessor {
    @Mutable
    @Accessor("team")
    Optional<TeamS2CPacket.SerializableTeam> getTeam();
}
