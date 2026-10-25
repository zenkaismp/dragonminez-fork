package com.dragonminez.client.render.shader;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.Reference;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DMZShaders {
	public static ShaderInstance auraShader;
	public static ShaderInstance auraSmooth3DShader;
	public static ShaderInstance auraSparking3DShader;
	public static ShaderInstance auraGod3DShader;
	public static ShaderInstance auraTrailShader;
	public static ShaderInstance lightningShader;
	public static ShaderInstance lightningBoltShader;
	public static ShaderInstance outlineShader;
	public static ShaderInstance outlineMaskTexShader;
	public static ShaderInstance ki3dShader;

	@SubscribeEvent
	public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {

		event.registerShader(new ShaderInstance(event.getResourceProvider(),
						new ResourceLocation(Reference.MOD_ID, "aura"),
						DefaultVertexFormat.POSITION_TEX),
				shaderInstance -> auraShader = shaderInstance);

		// v2.2 auras and lightning. A shader that fails to compile on some driver only leaves its field null (the
		// renderers fall back to the 2D aura or skip the effect) instead of failing the whole shader reload.
		registerOptional(event, "aura_smooth", DefaultVertexFormat.POSITION, shaderInstance -> auraSmooth3DShader = shaderInstance);
		registerOptional(event, "aura_sparking", DefaultVertexFormat.POSITION_COLOR_NORMAL, shaderInstance -> auraSparking3DShader = shaderInstance);
		registerOptional(event, "aura_god", DefaultVertexFormat.POSITION_TEX, shaderInstance -> auraGod3DShader = shaderInstance);
		registerOptional(event, "aura_trail", DefaultVertexFormat.POSITION_COLOR_NORMAL, shaderInstance -> auraTrailShader = shaderInstance);

		event.registerShader(new ShaderInstance(event.getResourceProvider(),
						new ResourceLocation(Reference.MOD_ID, "lightning"),
						DefaultVertexFormat.POSITION_COLOR_NORMAL),
				shaderInstance -> lightningShader = shaderInstance);

		registerOptional(event, "lightning_bolt", DefaultVertexFormat.POSITION_TEX_COLOR, shaderInstance -> lightningBoltShader = shaderInstance);

		event.registerShader(new ShaderInstance(event.getResourceProvider(),
						new ResourceLocation(Reference.MOD_ID, "transformation_mask"),
						DefaultVertexFormat.NEW_ENTITY),
				shaderInstance -> outlineShader = shaderInstance);

		event.registerShader(new ShaderInstance(event.getResourceProvider(),
						new ResourceLocation(Reference.MOD_ID, "transformation_mask_tex"),
						DefaultVertexFormat.NEW_ENTITY),
				shaderInstance -> outlineMaskTexShader = shaderInstance);

		event.registerShader(new ShaderInstance(event.getResourceProvider(),
						new ResourceLocation(Reference.MOD_ID, "kiattack"),
						DefaultVertexFormat.NEW_ENTITY),
				shaderInstance -> ki3dShader = shaderInstance);
	}

	private static void registerOptional(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> onLoaded) {
		try {
			event.registerShader(new ShaderInstance(event.getResourceProvider(),
					new ResourceLocation(Reference.MOD_ID, name), format), onLoaded);
		} catch (Exception e) {
			onLoaded.accept(null);
			LogUtil.error(Env.CLIENT, "Could not load shader dragonminez:{}, the effect that uses it stays off: {}", name, e.getMessage());
		}
	}
}
