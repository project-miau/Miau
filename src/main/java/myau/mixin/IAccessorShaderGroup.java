package myau.mixin;

import java.util.List;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderGroup.class)
public interface IAccessorShaderGroup {
  @Accessor("listShaders")
  List<Shader> myau$getListShaders();
}
