package hide.core;

import net.minecraftforge.fml.common.eventhandler.Event;

public class HideEvents {
	public static class TeamUpdateClient extends Event {

	}

	public static class TeamUpdate extends Event {
		public final String Player;

		public TeamUpdate(String player) {
			Player = player;
		}
	}
}
