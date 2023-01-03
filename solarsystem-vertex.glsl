#version 410

layout (location = 0) in vec3 vertPos;
layout (location = 2) in vec3 vertNormal;
layout (location = 1) in vec2 tex_coord;
out vec2 tc;

out vec3 varyingNormal;
out vec3 varyingLightDir;
out vec3 varyingVertPos;
out vec3 varyingHalfVector; // new output for Blinn-Phong

struct PositionalLight {
	vec4 ambient;
	vec4 diffuse;
	vec4 specular;
	vec3 position;
};

uniform PositionalLight light;
uniform float shininess;
uniform mat4 m_matrix;
uniform mat4 v_matrix;
uniform mat4 p_matrix;
uniform mat4 norm_matrix;
uniform sampler2D s;
uniform int isSun;

void main(void) {
	varyingVertPos = (m_matrix * vec4(vertPos,1.0)).xyz;
	varyingLightDir = light.position - varyingVertPos;
	varyingNormal = (norm_matrix * vec4(vertNormal,1.0)).xyz;

	// this is the only added line, for Blinn-Phong
	varyingHalfVector = normalize(normalize(varyingLightDir) + normalize(-varyingVertPos)).xyz;

	gl_Position = p_matrix * v_matrix * m_matrix * vec4(vertPos,1.0);
	tc = tex_coord;
}
