package hide.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.relauncher.Side;

/**イベントバスに登録される*/
public class HideSubSystem {

	private List<IHideSubSystem> subSystems = new ArrayList<>();

	public void register(FMLConstructionEvent event, IHideSubSystem system) {
		MinecraftForge.EVENT_BUS.register(system);
		system.init(event.getSide());
		subSystems.add(system);
	}

	public void serverStart(FMLServerStartingEvent event) {
		subSystems.forEach(sys -> sys.serverStart(event));
	}

	public void serverStop(FMLServerStoppedEvent event) {
		subSystems.forEach(sys -> sys.serverStop(event));
	}

	public interface IHideSubSystem {
		/**登録時に呼ばれる*/
		void init(Side side);

		default void serverStart(FMLServerStartingEvent event) {
		}

		default void serverStop(FMLServerStoppedEvent event) {
		}
	}
}
