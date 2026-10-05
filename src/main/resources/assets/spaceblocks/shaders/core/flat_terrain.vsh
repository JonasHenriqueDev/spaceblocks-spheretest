#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform vec3 FlatCamera;
uniform vec3 ChartOrigin;
uniform vec3 FaceNormal;
uniform vec3 FaceU;
uniform vec3 FaceV;
uniform vec3 FrameEast;
uniform vec3 FrameUp;
uniform vec3 FrameSouth;
uniform float FaceSize;
uniform float PlanetRadius;
uniform float FlatMorph;
uniform float CornerMorph;
uniform mat4 CachedMatrix;
uniform int CachedMode;
uniform int FaceIndex;
uniform float GuardSize;
uniform sampler2D SphereMap;
uniform vec3 PortalSourceOrigin;
uniform int PortalSourceFace;
out vec2 portalTargetUV;
out vec2 portalSourceUV;
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
#moj_import <spaceblocks:portal_common.glsl>
vec3 sampleSphere(vec2 uv,int face) {
 vec2 grid=clamp(uv*64.0/FaceSize,0.0,64.0);
 return normalize(texture(SphereMap,vec2((grid.x+.5)/65.0,(float(face)*65.0+grid.y+.5)/390.0)).rgb);
}
vec3 projectSphere(vec2 uv) {int face;vec2 folded=portalFold(uv,face);return sampleSphere(folded,face);}
vec3 visualPosition(vec3 world) {
    vec3 planar=world-FlatCamera;
    vec3 n=CachedMode>=2?sampleSphere(portalSourceUV,PortalSourceFace):projectSphere(world.xz-ChartOrigin.xz);
    vec3 anchor=projectSphere(FlatCamera.xz-ChartOrigin.xz);
    vec3 relative=n*(PlanetRadius+world.y-64.0)-anchor*(PlanetRadius+FlatCamera.y-64.0);
    vec3 curved=vec3(dot(relative,FrameEast),dot(relative,FrameUp),dot(relative,FrameSouth));
    float localGuard=smoothstep(8.0,24.0,length(planar));
    float horizon=smoothstep(64.0,192.0,length(planar.xz));
    float weight=max(max(FlatMorph*localGuard,horizon),CornerMorph);
    return CachedMode==3?curved:mix(planar,curved,weight);
}
void main() {
    vec3 world=CachedMode>=1?(CachedMatrix*vec4(Position+ChunkOffset,1.0)).xyz:Position+ChunkOffset+FlatCamera;
    vec2 local=world.xz-ChartOrigin.xz;
    portalTargetUV=local;portalSourceUV=(Position+ChunkOffset).xz-PortalSourceOrigin.xz;
    if(CachedMode==0&&(any(lessThan(local,vec2(-GuardSize)))||any(greaterThan(local,vec2(FaceSize+GuardSize))))){
        gl_Position=vec4(0,0,0,-1);vertexDistance=0;vertexColor=Color;texCoord0=UV0;return;
    }
    vec3 pos=visualPosition(world);
    gl_Position=ProjMat*ModelViewMat*vec4(pos,1.0);
    if(CachedMode==3){vec3 n=sampleSphere(portalSourceUV,PortalSourceFace);vec3 anchor=projectSphere(FlatCamera.xz-ChartOrigin.xz);if(dot(n,anchor*(PlanetRadius+FlatCamera.y-64.0)-n*(PlanetRadius+world.y-64.0))<0.0)gl_Position=vec4(0,0,0,-1);}

    vertexDistance=fog_distance(pos,FogShape);
    vertexColor=Color*minecraft_sample_lightmap(Sampler2,UV2);
    texCoord0=UV0;
}

