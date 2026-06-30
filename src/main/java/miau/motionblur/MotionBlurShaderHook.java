package miau.motionblur;

import net.minecraft.client.shader.ShaderGroup;

public interface MotionBlurShaderHook {
  ShaderGroup miau$getMotionBlurShader();

  void miau$setMotionBlurShader(ShaderGroup shaderGroup);
}
