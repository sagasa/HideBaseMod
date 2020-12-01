package hide.core.ops;

import java.io.File;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListOpsEntry;

/**エントリが無ければデフォルトを返す プロファイルがnullなので注意*/
public class HideUserListOps extends UserListOps {

	public HideUserListOps(File saveFile) {
		super(saveFile);
	}

	public static final UserListOpsEntry defaultEntry = new UserListOpsEntry(null, 0, false);

	private boolean opListEmpty() {
		return getValues().size() == 0;
	}

	@Override
	protected boolean hasEntry(GameProfile entry) {
		return !opListEmpty();
	}

	@Override
	public UserListOpsEntry getEntry(GameProfile obj) {
		UserListOpsEntry entry = super.getEntry(obj);
		return entry == null && !opListEmpty() ? defaultEntry : entry;
	}
}