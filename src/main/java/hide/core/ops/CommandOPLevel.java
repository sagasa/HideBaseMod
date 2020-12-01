package hide.core.ops;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class CommandOPLevel extends CommandBase {

	@Override
	public String getName() {
		return "oplevel";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.oplevel.usage";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 3;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 2 && args[0].length() > 0) {
			GameProfile gameprofile = server.getPlayerProfileCache().getGameProfileForUsername(args[0]);

			if (gameprofile == null && !OPUtil.setOpLevel(server, gameprofile, parseInt(args[1], 0, 4))) {
				throw new CommandException("commands.oplevel.failed", new Object[] { args[0] });
			}
			notifyCommandListener(sender, this, "commands.oplevel.success", new Object[] { args[0], args[1] });
		} else {
			throw new WrongUsageException("commands.oplevel.usage", new Object[0]);
		}
	}

	/**
	 * Get a list of options for when the user presses the TAB key
	 */
	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 1) {
			String s = args[args.length - 1];
			List<String> list = Lists.<String> newArrayList();

			for (GameProfile gameprofile : server.getOnlinePlayerProfiles()) {
				if (!server.getPlayerList().canSendCommands(gameprofile)
						&& doesStringStartWith(s, gameprofile.getName())) {
					list.add(gameprofile.getName());
				}
			}

			return list;
		}
		return Collections.<String> emptyList();
	}

}