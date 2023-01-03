#version 410

in vec3 varyingNormal;
in vec3 varyingLightDir;
in vec3 varyingVertPos;
in vec3 varyingHalfVector; // new input for Blinn-Phong

uniform sampler2D s;
in vec2 tc;

out vec4 fragColor;

struct PositionalLight {
	vec4 ambient;  
	vec4 diffuse;  
	vec4 specular;  
	vec3 position;
};


uniform float shininess;
uniform float linAtt;
uniform PositionalLight light;

uniform mat4 v_matrix;
uniform int isSun;
uniform vec4 color;

void main(void) {
	// normalize the light, normal, and view vectors:
	vec3 L = normalize(varyingLightDir);
	vec3 N = normalize(varyingNormal);
	vec3 V = normalize(-v_matrix[3].xyz - varyingVertPos);
	
	// get the angle between the light and surface normal:
	float cosTheta = dot(L,N);
	
	// halfway vector varyingHalfVector was computed in the vertex shader,
	// and interpolated prior to reaching the fragment shader.
	// It is copied into variable H here for convenience later.
	vec3 H = normalize(varyingHalfVector);
	
	// get angle between the normal and the halfway vector
	float cosPhi = dot(H,N);

	// compute ADS contributions (per pixel):
	vec3 ambient = light.ambient.xyz;
	vec3 diffuse = light.diffuse.xyz  * max(cosTheta,0.0);
	vec3 specular = light.specular.xyz * pow(max(cosPhi,0.0), shininess);

	float dist = abs(distance(varyingVertPos,light.position));
	float a = 1 /(dist * linAtt + 1);

	if(isSun == 1){
		fragColor = texture(s, tc);
	}else{
		fragColor = vec4(ambient + (diffuse*a), 1.0) * texture(s, tc) * color + vec4((specular*a),1.0);
	}
}
