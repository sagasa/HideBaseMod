package hide.core.asm;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;

public class HideBaseTransformer implements IClassTransformer {
	// IClassTransformerにより呼ばれる書き換え用のメソッド。

	private static final Logger log = LogManager.getLogger();

	private static Multimap<String, TransformEntry> transformMap = Multimaps.newListMultimap(new HashMap<>(),
			ArrayList::new);

	private static void register(String className, String methodName[],
			Function<MethodVisitor, MethodVisitor> methodVisitor) {
		register(className, methodName, methodVisitor, false);
	}

	private static void register(String className, String methodName[],
			Function<MethodVisitor, MethodVisitor> methodVisitor, boolean isClient) {
		transformMap.put(className, new TransformEntry(methodName, methodVisitor, isClient));
	}

	static {
		/* Modロード前に削除処理 */

		register("net.minecraftforge.fml.common.Loader", new String[] { "initializeLoader" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if ((IRETURN <= opcode && opcode <= RETURN) || opcode == ATHROW) {
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookPreLoadMod",
									Type.getMethodDescriptor(Type.VOID_TYPE), false);
						}
						super.visitInsn(opcode);
					}
				});
		/* OPリストを乗っ取り */

		register("net.minecraft.server.management.PlayerList", new String[] { "<init>" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					boolean flag = false;

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if (type.equals("net/minecraft/server/management/UserListOps")) {
							flag = true;
							return;
						}
						super.visitTypeInsn(opcode, type);
					}

					@Override
					public void visitInsn(int opcode) {
						if (opcode == DUP && flag) {
							flag = false;
							return;
						}
						super.visitInsn(opcode);
					}

					//代わりを挿入
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc,
							boolean itf) {
						if (owner.equals("net/minecraft/server/management/UserListOps")) {
							System.out.println("TEST " + opcode + " " + owner + " " + name + " " + desc);
							opcode = INVOKESTATIC;
							owner = "hide/core/asm/HideCoreHook";
							name = "getHideListOps";
							desc = Type.getMethodDescriptor(Type.getObjectType(
									"net/minecraft/server/management/UserListOps"),
									Type.getObjectType(
											"java/io/File"));
						}
						super.visitMethodInsn(opcode, owner, name, desc, itf);
					}
				});
		/* GuiInGametの初期化 */
		register("net.minecraft.client.gui.GuiIngame", new String[] { "<init>" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if ((IRETURN <= opcode && opcode <= RETURN) || opcode == ATHROW) {
							mv.visitVarInsn(ALOAD, 0);
							mv.visitVarInsn(ALOAD, 1);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "getGuiNewChat",
									Type.getMethodDescriptor(Type.getObjectType("net/minecraft/client/gui/GuiNewChat"),
											Type.getObjectType("net/minecraft/client/Minecraft")),
									false);
							mv.visitFieldInsn(PUTFIELD, "net/minecraft/client/gui/GuiIngame", "field_73840_e",
									Type.getDescriptor(GuiNewChat.class));
						}
						super.visitInsn(opcode);
					}
				}, true);
		/* ハンドシェイクコーデックのコンストラクタにフック */
		register(
				"net.minecraftforge.fml.common.network.handshake.FMLHandshakeCodec", new String[] { "<init>" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
							mv.visitVarInsn(ALOAD, 0);
							mv.visitInsn(ICONST_4);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "getAdditionalClass",
									Type.getMethodDescriptor(Type.getObjectType("java/lang/Class")),
									false);
							mv.visitMethodInsn(INVOKEVIRTUAL,
									"net/minecraftforge/fml/common/network/FMLIndexedMessageToMessageCodec",
									"addDiscriminator",
									Type.getMethodDescriptor(Type.getObjectType(
											"net/minecraftforge/fml/common/network/FMLIndexedMessageToMessageCodec"),
											Type.INT_TYPE,
											Type.getObjectType("java/lang/Class")),
									false);
						}
						super.visitInsn(opcode);
					}
				});
		/* ログインハンドシェイクのクライアント側Hello88行目後にフック */
		register(
				"net.minecraftforge.fml.common.network.handshake.FMLHandshakeClientState$2", new String[] { "accept" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitVarInsn(int opcode, int var) {
						super.visitVarInsn(opcode, var);
						if (var == 5 && opcode == ASTORE) {
							mv.visitVarInsn(ALOAD, 1);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookOnClientHello",
									Type.getMethodDescriptor(Type.VOID_TYPE,
											Type.getObjectType(
													"io/netty/channel/ChannelHandlerContext")),
									false);
						}
					}
				});
		/* ログインハンドシェイクのサーバー側Hello開始直後にフック */
		register(
				"net.minecraftforge.fml.common.network.handshake.FMLHandshakeServerState$2", new String[] { "accept" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitCode() {
						super.visitCode();
						mv.visitVarInsn(ALOAD, 1);
						mv.visitVarInsn(ALOAD, 2);
						mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookOnServerHello", Type
								.getMethodDescriptor(Type.BOOLEAN_TYPE,
										Type.getObjectType(
												"io/netty/channel/ChannelHandlerContext"),
										Type.getObjectType(
												"net/minecraftforge/fml/common/network/handshake/FMLHandshakeMessage")),
								false);
						Label skip = new Label();
						mv.visitJumpInsn(IFEQ, skip);
						mv.visitInsn(RETURN);
						mv.visitLabel(skip);
					}
				});
		/* ログインハンドシェイクのクライアント側WAITINGSERVERDATA開始直後にフック */
		register(
				"net.minecraftforge.fml.common.network.handshake.FMLHandshakeClientState$3", new String[] { "accept" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitCode() {
						super.visitCode();
						mv.visitVarInsn(ALOAD, 1);
						mv.visitVarInsn(ALOAD, 2);
						mv.visitVarInsn(ALOAD, 3);
						mv.visitFieldInsn(GETSTATIC,
								"net/minecraftforge/fml/common/network/handshake/FMLHandshakeClientState$3", "ERROR",
								"Lnet/minecraftforge/fml/common/network/handshake/FMLHandshakeClientState;");
						mv.visitMethodInsn(
								INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookOnClientReceveServerData", Type
										.getMethodDescriptor(Type.BOOLEAN_TYPE,
												Type.getObjectType("io/netty/channel/ChannelHandlerContext"),
												Type.getObjectType(
														"net/minecraftforge/fml/common/network/handshake/FMLHandshakeMessage"),
												Type.getObjectType("java/util/function/Consumer"),
												Type.getObjectType("java/lang/Object")),
								false);
						Label skip = new Label();
						mv.visitJumpInsn(IFEQ, skip);
						mv.visitInsn(RETURN);
						mv.visitLabel(skip);
					}
				});
		/* ログイン時にIPとアドレスをもらう */
		register("net.minecraft.client.multiplayer.GuiConnecting",
				new String[] { "connect", "func_146367_a" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitCode() {
						super.visitCode();
						mv.visitVarInsn(ALOAD, 1);
						mv.visitVarInsn(ILOAD, 2);
						mv.visitMethodInsn(
								INVOKESTATIC, "hide/core/asm/HideCoreHook", "onConnectServer", Type
										.getMethodDescriptor(Type.VOID_TYPE,
												Type.getObjectType("java/lang/String"),
												Type.INT_TYPE),
								false);
					}
				}, true);
		/* 腕の向きを上書き */
		register("net.minecraft.client.model.ModelBiped",
				new String[] { "setRotationAngles", "func_78087_a" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
							mv.visitVarInsn(ALOAD, 0);
							mv.visitVarInsn(ALOAD, 7);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookOnSetAngle",
									Type.getMethodDescriptor(Type.VOID_TYPE,
											Type.getObjectType("net/minecraft/client/model/ModelBiped"),
											Type.getObjectType("net/minecraft/entity/Entity")),
									false);
						}
						super.visitInsn(opcode);
					}
				}, true);
		/* レンダーのコンストラクタにフック */
		register("net.minecraft.client.renderer.entity.RenderLivingBase", new String[] { "<init>" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
							mv.visitVarInsn(ALOAD, 0);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook",
									"hookOnMakeLivingRender",
									Type.getMethodDescriptor(Type.VOID_TYPE, Type
											.getObjectType(
													"net/minecraft/client/renderer/entity/RenderLivingBase")),
									false);
						}
						super.visitInsn(opcode);
					}
				}, true);
		/* 左クリックのアニメーションフック */
		register("net.minecraft.client.Minecraft", new String[] { "clickMouse", "func_147116_af" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitCode() {
						super.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookOnLeftClick", Type
								.getMethodDescriptor(Type.BOOLEAN_TYPE,
										Type.getObjectType("net/minecraft/client/Minecraft")),
								false);
						Label skip = new Label();
						mv.visitJumpInsn(IFEQ, skip);
						mv.visitInsn(RETURN);
						mv.visitLabel(skip);
					}
				}, true);
		/* チャットの制限解除 */
		register("net.minecraft.util.ChatAllowedCharacters",
				new String[] { "isAllowedCharacter", "func_71565_a" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitIntInsn(int opcode, int operand) {

						if (opcode == SIPUSH || operand == 167)
							super.visitIntInsn(opcode, 127);
						else
							super.visitIntInsn(opcode, operand);
					}
				});
		/* チャットの幅変更 */
		register("net.minecraft.client.gui.GuiNewChat",
				new String[] { "calculateChatboxWidth", "func_146233_a" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitLdcInsn(Object cst) {
						if (cst.equals(280.0f))
							super.visitLdcInsn(320.0f);
						else
							super.visitLdcInsn(cst);
					}
				});

		/* プレイヤーがチームに追加されたら */
		register("net.minecraft.scoreboard.ServerScoreboard",
				new String[] { "addPlayerToTeam", "func_151392_a" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					boolean flag = false;

					@Override
					public void visitInsn(int opcode) {
						if (opcode == ICONST_1) {
							if (flag) {
								mv.visitVarInsn(ALOAD, 1);
								mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "onChangePlayerTeam",
										Type.getMethodDescriptor(Type.VOID_TYPE,
												Type.getObjectType(
														"java/lang/String")),
										false);
							} else
								flag = true;
						}
						super.visitInsn(opcode);
					}
				});
		/* プレイヤーがチームから消されたら */
		register("net.minecraft.scoreboard.ServerScoreboard",
				new String[] { "removePlayerFromTeam", "func_96512_b" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if (opcode == RETURN) {
							mv.visitVarInsn(ALOAD, 1);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "onChangePlayerTeam",
									Type.getMethodDescriptor(Type.VOID_TYPE,
											Type.getObjectType(
													"java/lang/String")),
									false);
						}
						super.visitInsn(opcode);
					}
				});
		/* プレイヤーがチームに追加されたら Client*/
		register("net.minecraft.client.network.NetHandlerPlayClient",
				new String[] { "handleTeams", "func_147247_a" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						super.visitMethodInsn(opcode, owner, name, desc, itf);
						if (name.equals("addPlayerToTeam")) {
							mv.visitVarInsn(ALOAD, 5);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "onChangePlayerTeamClient",
									Type.getMethodDescriptor(Type.VOID_TYPE,
											Type.getObjectType(
													"java/lang/String")),
									false);
						} else if (name.equals("removePlayerFromTeam")) {
							mv.visitVarInsn(ALOAD, 5);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "onChangePlayerTeamClient",
									Type.getMethodDescriptor(Type.VOID_TYPE,
											Type.getObjectType(
													"java/lang/String")),
									false);
						}
					}
				});
		/* プレイヤーがチームから消されたら Client */
		register("net.minecraft.scoreboard.ServerScoreboard",
				new String[] { "removePlayerFromTeam", "func_96512_b" },
				(mv) -> new MethodVisitor(ASM4, mv) {
					@Override
					public void visitInsn(int opcode) {
						if (opcode == RETURN) {
							mv.visitVarInsn(ALOAD, 1);
							mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "onChangePlayerTeam",
									Type.getMethodDescriptor(Type.VOID_TYPE,
											Type.getObjectType(
													"java/lang/String")),
									false);
						}
						super.visitInsn(opcode);
					}
				});
	}

	@SuppressWarnings("unused")
	private static void name() {
		new MethodVisitor(ASM4, null) {
			@Override
			public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
				super.visitMethodInsn(opcode, owner, name, desc, itf);
				System.out.println("FIND " + opcode + " " + owner + " " + name + " " + desc + " " + itf);
			}
		};
	}

	@Override
	public byte[] transform(final String name, final String transformedName, byte[] bytes) {
		if (transformMap.containsKey(name)) {
			ClassReader cr = new ClassReader(bytes);
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = new ClassVisitor(ASM4, cw) {
				// クラス内のメソッドを訪れる。
				@Override
				public MethodVisitor visitMethod(int access, String methodName, String desc, String signature,
						String[] exceptions) {
					MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);
					// メゾット名
					String s1 = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(name, methodName, desc);
					for (TransformEntry entry : transformMap.get(name)) {
						//サイドフィルタ
						if (!entry.isClient || FMLLaunchHandler.side() == Side.CLIENT)
							mv = entry.apply(s1, mv);
					}
					return mv;
				}
			};
			cr.accept(cv, ClassReader.EXPAND_FRAMES);
			return cw.toByteArray();
		}
		return bytes;
	}

	static class TransformEntry {
		TransformEntry(String methodName[], Function<MethodVisitor, MethodVisitor> methodVisitor,
				boolean isclient) {
			MethodName = methodName;
			MethodVisitor = methodVisitor;
			isClient = isclient;
		}

		String MethodName[];

		boolean isClient = false;
		Function<MethodVisitor, MethodVisitor> MethodVisitor;

		/** 指定のメゾットを書き換え */
		MethodVisitor apply(final String methodName, MethodVisitor mv) {
			for (String str : MethodName) {
				if (str.equals(methodName)) {
					mv = MethodVisitor.apply(mv);
					break;
				}
			}
			return mv;
		}
	}
}