package hide.core.asm;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

public class HideBaseTransformer implements IClassTransformer {
	// IClassTransformerにより呼ばれる書き換え用のメソッド。

	private static final Logger log = LogManager.getLogger();

	private static List<TransformEntry> transformEntries = new ArrayList();

	static {
		/* Modロード前に削除処理 */
		transformEntries
				.add(new TransformEntry("net.minecraftforge.fml.common.Loader", new String[] { "initializeLoader" },
						(mv) -> new MethodVisitor(ASM4, mv) {
							@Override
							public void visitInsn(int opcode) {
								if ((IRETURN <= opcode && opcode <= RETURN) || opcode == ATHROW) {
									mv.visitMethodInsn(INVOKESTATIC, "hide/core/asm/HideCoreHook", "hookPreLoadMod",
											Type.getMethodDescriptor(Type.VOID_TYPE), false);
								}
								super.visitInsn(opcode);
							}
						}));
		/* OPリストを乗っ取り */
		transformEntries
				.add(new TransformEntry("net.minecraft.server.management.PlayerList", new String[] { "<init>" },
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
						}));
		/* GuiInGametの初期化 */
		transformEntries.add(new TransformEntry("net.minecraft.client.gui.GuiIngame", new String[] { "<init>" },
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
				}, true));
		/* ハンドシェイクコーデックのコンストラクタにフック */
		transformEntries.add(new TransformEntry(
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
				}));
		/* ログインハンドシェイクのクライアント側Hello88行目後にフック */
		transformEntries.add(new TransformEntry(
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
				}));
		/* ログインハンドシェイクのサーバー側Hello開始直後にフック */
		transformEntries.add(new TransformEntry(
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
				}));
		/* ログインハンドシェイクのクライアント側WAITINGSERVERDATA開始直後にフック */
		transformEntries.add(new TransformEntry(
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
				}));
		/* ログイン時にIPとアドレスをもらう */
		transformEntries.add(new TransformEntry("net.minecraft.client.multiplayer.GuiConnecting",
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
				}, true));
		/* 腕の向きを上書き */
		transformEntries.add(new TransformEntry("net.minecraft.client.model.ModelBiped",
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
				}, true));
		/* レンダーのコンストラクタにフック */
		transformEntries.add(
				new TransformEntry("net.minecraft.client.renderer.entity.RenderLivingBase", new String[] { "<init>" },
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
						}, true));
		/* 左クリックのアニメーションフック */
		transformEntries.add(
				new TransformEntry("net.minecraft.client.Minecraft", new String[] { "clickMouse", "func_147116_af" },
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
						}, true));
	}

	@Override
	public byte[] transform(final String name, final String transformedName, byte[] bytes) {
		for (TransformEntry transform : transformEntries) {
			bytes = transform.apply(name, transformedName, bytes);
		}
		return bytes;
	}

	static class TransformEntry {

		TransformEntry(String className, String methodName[], Function<MethodVisitor, MethodVisitor> methodVisitor) {
			this(className, methodName, methodVisitor, false);
		}

		TransformEntry(String className, String methodName[], Function<MethodVisitor, MethodVisitor> methodVisitor,
				boolean isclient) {
			ClassName = className;
			MethodName = methodName;
			MethodVisitor = methodVisitor;
			isClient = isclient;
		}

		String ClassName;
		String MethodName[];

		boolean isClient = false;
		Function<MethodVisitor, MethodVisitor> MethodVisitor;

		public TransformEntry setIsClient(boolean bool) {
			isClient = bool;
			return this;
		}

		/** 指定のメゾットを書き換え */
		byte[] apply(final String name, final String transformedName, byte[] bytes) {
			if (!ClassName.equals(transformedName))
				return bytes;
			FMLLaunchHandler.side();
			ClassReader cr = new ClassReader(bytes);
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = new ClassVisitor(ASM4, cw) {
				// クラス内のメソッドを訪れる。
				@Override
				public MethodVisitor visitMethod(int access, String methodName, String desc, String signature,
						String[] exceptions) {
					MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);
					// 呼び出し元のメソッドを参照していることを確認する。
					String s1 = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(name, methodName, desc);
					//System.out.println("serch "+s1+" , "+ArrayUtils.toString(MethodName));
					for (String str : MethodName) {
						if (str.equals(s1)) {
							//System.out.println("HIT!!!!!!!!!! " + s1);
							mv = MethodVisitor.apply(mv);
							break;
						}
					}

					return mv;
				}
			};
			cr.accept(cv, ClassReader.EXPAND_FRAMES);
			return cw.toByteArray();
		}
	}
}