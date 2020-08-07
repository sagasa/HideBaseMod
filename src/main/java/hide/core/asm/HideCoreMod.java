package hide.core.asm;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

public class HideCoreMod implements IFMLLoadingPlugin {
    //書き換え機能を実装したクラス一覧を渡す関数。書き方はパッケージ名+クラス名。
    @Override
    public String[] getASMTransformerClass() {
    	System.out.println("getASMTransformerClass");
    	return new String[]{"hide.core.asm.HideBaseTransformer"};
    }

    @Override
    public String getSetupClass() {
    	System.out.println("getSetupClass");
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    	System.out.println("injectData "+data);
    }

    @Override
    public String getAccessTransformerClass() {
    	System.out.println("getAccessTransformerClass");
        return null;
    }

    @Override
    public String getModContainerClass() {
    	System.out.println("getModContainerClass");
        return null;
    }
}