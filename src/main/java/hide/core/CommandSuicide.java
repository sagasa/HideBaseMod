package hide.core;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public class CommandSuicide extends CommandBase {

	@Override
	public String getName() {
		return "suicide";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.suicide.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		EntityPlayerMP player = getCommandSenderAsPlayer(sender);
		player.onKillCommand();
		notifyCommandListener(sender, this, "commands.kill.successful", new Object[] { player.getDisplayName() });

	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}
}