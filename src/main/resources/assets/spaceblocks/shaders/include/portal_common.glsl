void basis(vec3 n,out vec3 u,out vec3 v,out int face) {
    if(n.z>.5){u=vec3(1,0,0);v=vec3(0,-1,0);face=0;}
    else if(n.x>.5){u=vec3(0,0,-1);v=vec3(0,-1,0);face=1;}
    else if(n.z<-.5){u=vec3(-1,0,0);v=vec3(0,-1,0);face=2;}
    else if(n.x<-.5){u=vec3(0,0,1);v=vec3(0,-1,0);face=3;}
    else if(n.y>.5){u=vec3(1,0,0);v=vec3(0,0,1);face=4;}
    else{u=vec3(1,0,0);v=vec3(0,0,-1);face=5;}
}
vec2 portalFold(vec2 uv,out int face) {
    vec2 q=uv*2.0/FaceSize-1.0;
    face=FaceIndex;vec3 n,u,v;
    if(face==0)n=vec3(0,0,1);else if(face==1)n=vec3(1,0,0);else if(face==2)n=vec3(0,0,-1);else if(face==3)n=vec3(-1,0,0);else if(face==4)n=vec3(0,1,0);else n=vec3(0,-1,0);basis(n,u,v,face);
    for(int iteration=0;iteration<16;iteration++) {
        if(abs(q.x)<=1.0&&abs(q.y)<=1.0)break;
        bool alongU=abs(q.x)>=abs(q.y);
        float across=alongU?q.x:q.y,parallel=alongU?q.y:q.x;
        vec3 outward=(alongU?u:v)*sign(across);
        vec3 folded=outward+n*(2.0-abs(across))+(alongU?v:u)*parallel;
        n=outward;basis(n,u,v,face);q=vec2(dot(folded,u),dot(folded,v));
    }
return (q+1.0)*FaceSize*.5;
}
