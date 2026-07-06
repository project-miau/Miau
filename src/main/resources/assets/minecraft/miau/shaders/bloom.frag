#version 120

uniform sampler2D inTexture;
uniform sampler2D textureToCheck;
uniform vec2 texelSize, direction;
uniform float radius;
uniform float weights[256];

#define offset direction * texelSize

void main() {
    vec4 innerColor = texture2D(inTexture, gl_TexCoord[0].st);
    innerColor.rgb *= innerColor.a;
    innerColor *= weights[0];

    for (float r = 1.0; r <= radius; r += 1.0) {
        vec4 colorCurrent1 = texture2D(inTexture, gl_TexCoord[0].st + offset * r);
        vec4 colorCurrent2 = texture2D(inTexture, gl_TexCoord[0].st - offset * r);
        colorCurrent1.rgb *= colorCurrent1.a;
        colorCurrent2.rgb *= colorCurrent2.a;
        innerColor += (colorCurrent1 + colorCurrent2) * weights[int(r)];
    }

    gl_FragColor = vec4(innerColor.rgb / innerColor.a, innerColor.a);
}
