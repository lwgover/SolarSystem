import org.joml.*;
import static java.lang.Math.*;

public class Sphere
{
    private int numVertices, numIndices, prec;
    private int[] indices;
    private Vector3f[] vertices;
    private Vector2f[] texCoords;
    private Vector3f[] normals;
    private Vector3f[] tangents;

    public Sphere()
    {	prec = 48;
        InitSphere();
    }

    public Sphere(int p)
    {	prec = p;
        InitSphere();
    }

    private void InitSphere()
    {	numVertices = (prec+1) * (prec+1);
        numIndices = prec * prec * 6;
        indices = new int[numIndices];
        vertices = new Vector3f[numVertices];
        texCoords = new Vector2f[numVertices];
        normals = new Vector3f[numVertices];
        tangents = new Vector3f[numVertices];

        for (int i=0; i<numVertices; i++)
        {	vertices[i] = new Vector3f();
            texCoords[i] = new Vector2f();
            normals[i] = new Vector3f();
            tangents[i] = new Vector3f();
        }

        // calculate triangle vertices
        for (int i=0; i<=prec; i++)
        {	for (int j=0; j<=prec; j++)
        {	float y = (float)cos(toRadians(180-i*180/prec));
            float x = -(float)cos(toRadians(j*360/(float)prec))*(float)abs(cos(asin(y)));
            float z = (float)sin(toRadians(j*360/(float)prec))*(float)abs(cos(asin(y)));
            vertices[i*(prec+1)+j].set(x,y,z);
            texCoords[i*(prec+1)+j].set((float)j/prec, (float)i/prec);
            normals[i*(prec+1)+j].set(x,y,z);

            // calculate tangent vector
            if (((x==0) && (y==1) && (z==0)) || ((x==0) && (y==-1) && (z==0)))
            {	tangents[i*(prec+1)+j].set(0.0f, 0.0f, -1.0f);
            }
            else
            {	tangents[i*(prec+1)+j] = (new Vector3f(0,1,0)).cross(new Vector3f(x,y,z));
            }	}	}

        // calculate triangle indices
        for(int i=0; i<prec; i++)
        {	for(int j=0; j<prec; j++)
        {	indices[6*(i*prec+j)+0] = i*(prec+1)+j;
            indices[6*(i*prec+j)+1] = i*(prec+1)+j+1;
            indices[6*(i*prec+j)+2] = (i+1)*(prec+1)+j;
            indices[6*(i*prec+j)+3] = i*(prec+1)+j+1;
            indices[6*(i*prec+j)+4] = (i+1)*(prec+1)+j+1;
            indices[6*(i*prec+j)+5] = (i+1)*(prec+1)+j;
        }	}	}

    public int getNumIndices() { return numIndices; }
    public int getNumVertices() { return numVertices; }
    public int[] getIndices() { return indices; }
    public Vector3f[] getVertices() { return vertices; }
    public Vector2f[] getTexCoords() { return texCoords; }
    public Vector3f[] getNormals() { return normals; }
    public Vector3f[] getTangents() { return tangents; }
}
