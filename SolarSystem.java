import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.FileSystemException;
import java.security.acl.LastOwnerException;
import java.util.*;

import java.nio.*;
import javax.swing.*;
import java.lang.Math;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.util.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.common.nio.Buffers;
import org.joml.*;

public class SolarSystem extends JFrame implements GLEventListener {
    // constants
    private static final int WINDOW_WIDTH = 800, WINDOW_HEIGHT = 800;
    private static final String WINDOW_TITLE = "Solar System Demo";
    private static final String VERTEX_SHADER_FILE = "solarsystem-vertex.glsl",
            FRAGMENT_SHADER_FILE = "solarsystem-fragment.glsl";

    // window fields
    private GLCanvas glCanvas;
    private int renderingProgram;
    private int[] vao = new int[1];
    private int[] vbo = new int[4];
    private Vector3f SphereLoc = new Vector3f(0,0,-1);
    private Vector3f cameraLoc;

    private Sphere mySphere;
    private int numSphereVertices, numSphereIndices;

    private Vector3f initialLightLoc = new Vector3f(5.0f, 2.0f, 2.0f);
    private float amt = 0.0f;
    private double prevTime;
    private double elapsedTime;

    // allocate variables for display() function
    private FloatBuffer vals = Buffers.newDirectFloatBuffer(16);
    private Matrix4f pMat = new Matrix4f();  // perspective matrix
    private Matrix4f vMat = new Matrix4f();  // view matrix
    private Matrix4f mMat = new Matrix4f();  // model matrix
    private Matrix4f invTrMat = new Matrix4f(); // inverse-transpose
    private int mLoc, vLoc, pLoc, nLoc, isSunLoc;;
    private int shininessLoc, ambLoc, diffLoc, specLoc, posLoc,colorLoc,linAttLoc;
    private float aspect;
    private Vector3f currentLightPos = new Vector3f();
    private float[] lightPos = new float[3];

    private static float ambientBase;
    private static float specularBase;
    private static float diffuseBase;
    private static float linearAttenuationConstant;

    float[] lightAmbient;
    float[] lightDiffuse;
    float[] lightSpecular;
    private static float[] lightColor;

    SolarBody[] solarBodies;
    Planet[] planets;
    int[] textureIDs;
    boolean[] isSun;

    /**
     * Constructor for the containing window.
     */

    public SolarSystem(String fileName) throws FileSystemException, FileNotFoundException {
        makeSolarSystem(fileName);
        setTitle(WINDOW_TITLE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        GLProfile glp = GLProfile.getMaxProgrammableCore(true);
        GLCapabilities caps = new GLCapabilities(glp);
        this.glCanvas = new GLCanvas(caps);
        glCanvas.addGLEventListener(this);
        this.add(glCanvas);
        this.setVisible(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Animator animator = new Animator(glCanvas);
        animator.start();
    }

    public static void main(String[] args){
        if(!checkArgs(args)){
            System.exit(0);
        }
        try {
            new SolarSystem(args[0]);
        }catch (FileNotFoundException e) {
            System.err.println("Could not find your .sol file!");
            System.exit(0);
        }catch(FileSystemException e){
            System.err.println(e.getMessage());
            System.exit(0);
        }
    }
    private static boolean checkArgs(String[] args){
        if(args.length > 1){
            System.err.println("Please only provide 1 file");
            return false;
        }
        if(args.length <= 0){
            System.err.println("Please provide a .sol file");
            return false;
        }
        if(args[0].substring(args[0].length()-5).equals(".sol")){
            System.err.println("Please provide a .sol file");
            return false;
        }
        return true;
    }
    private void makeSolarSystem(String fileName) throws FileNotFoundException,FileSystemException {
        File solFile = new File(fileName);
        Scanner fileReader = new Scanner(solFile);
        try {
            String[] cameraPosStrings = fileReader.nextLine().split("\t");
            cameraLoc = new Vector3f(Float.parseFloat(cameraPosStrings[0]), Float.parseFloat(cameraPosStrings[1]), Float.parseFloat(cameraPosStrings[2]));
            String[] lightStrings = fileReader.nextLine().split("\t");
            lightColor = new float[]{Float.parseFloat(lightStrings[0]), Float.parseFloat(lightStrings[1]), Float.parseFloat(lightStrings[2]),1.0f};
            ambientBase = Float.parseFloat(lightStrings[3]);
            lightAmbient = new float[] {ambientBase, ambientBase, ambientBase, 1.0f };
            diffuseBase = Float.parseFloat(lightStrings[4]);
            lightDiffuse = new float[] {diffuseBase, diffuseBase, diffuseBase, 1.0f };
            specularBase = Float.parseFloat(lightStrings[5]);
            lightSpecular = new float[] {specularBase, specularBase, specularBase, 1.0f };
            linearAttenuationConstant = Float.parseFloat(lightStrings[6]);
        }catch(Exception e) {
            throw new FileSystemException("The light and/or coloring formatting were incorrect");
        }
        try{
            LinkedList<SolarBody> lastPlanet = new LinkedList<SolarBody>();
            lastPlanet.addFirst(null);
            int lastNumTabs = -1;
            Sun sun = null;
            ArrayList<Planet> planets = new ArrayList<Planet>();
            while (fileReader.hasNextLine()) {
                String nextLine = fileReader.nextLine();
                int numTabs = 0;
                while(nextLine.charAt(numTabs) == '\t'){
                    numTabs++;
                }
                nextLine = nextLine.substring(numTabs);
                if(numTabs <= lastNumTabs){
                    for(int i = 0; i <= lastNumTabs-numTabs; i++)
                        lastPlanet.pop();
                }
                lastNumTabs = numTabs;

                String[] planetStrings = nextLine.split("\t");
                String planetTexture = planetStrings[0];
                float radius = Float.parseFloat(planetStrings[1]);
                float rotationPeriod = Float.parseFloat(planetStrings[2]);
                if(planetStrings.length <=3) {// if sun
                    sun = new Sun(planetTexture,radius,rotationPeriod);
                    lastPlanet.addFirst(new Sun(planetTexture,radius,rotationPeriod));
                    continue;
                }
                float distFromCenter = Float.parseFloat(planetStrings[3]);
                float orbitingPeriod = Float.parseFloat(planetStrings[4]);
                float specularComponent = Float.parseFloat(planetStrings[5]);
                planets.add(new Planet(planetTexture,radius,rotationPeriod,distFromCenter,orbitingPeriod,specularComponent,lastPlanet.getFirst()));
                lastPlanet.addFirst(planets.get(planets.size()-1));
            }
            SolarBody[] solSystem = new SolarBody[planets.size()+1];
            Planet[] allPlanets = new Planet[planets.size()];
            int counter = 1;
            solSystem[0] = sun;
            for(Planet sb: planets){
                solSystem[counter] = sb;
                allPlanets[counter-1] = sb;
                counter++;
            }
            this.solarBodies = solSystem;
            this.planets = allPlanets;
        }catch(Exception e){
            throw new FileSystemException("One or more Planets were incorrectly formatted");
        }
    }

    @Override
    public void init(GLAutoDrawable glAutoDrawable) {
        GL4 gl = (GL4) GLContext.getCurrentGL();
        renderingProgram = Utils.createShaderProgram(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE);

        prevTime = System.currentTimeMillis();

        aspect = (float) glCanvas.getWidth() / (float) glCanvas.getHeight();
        pMat.setPerspective((float) Math.toRadians(60.0f), aspect, 0.1f, 1000.0f);

        setupVertices();
        setupTextures();
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        System.out.println("init"); // need this to run on mac, appoligies if I forget to delete
    }
    public void setupTextures(){
        GL4 gl = (GL4) GLContext.getCurrentGL();
        textureIDs = new int[solarBodies.length];
        for(int i = 0; i < textureIDs.length; i++){
            textureIDs[i] = Utils.loadTexture(solarBodies[i].getTextureFile());
        }
    }

    @Override
    public void dispose(GLAutoDrawable glAutoDrawable) {}

    @Override
    public void display(GLAutoDrawable glAutoDrawable) {
        GL4 gl = (GL4) GLContext.getCurrentGL();
        gl.glClear(GL_COLOR_BUFFER_BIT);
        gl.glClear(GL_DEPTH_BUFFER_BIT);

        gl.glUseProgram(renderingProgram);

        mLoc = gl.glGetUniformLocation(renderingProgram, "m_matrix");
        vLoc = gl.glGetUniformLocation(renderingProgram, "v_matrix");
        pLoc = gl.glGetUniformLocation(renderingProgram, "p_matrix");
        nLoc = gl.glGetUniformLocation(renderingProgram, "norm_matrix");
        isSunLoc = gl.glGetUniformLocation(renderingProgram, "isSun");
        shininessLoc = gl.glGetUniformLocation(renderingProgram, "shininess");

        Vector3f front = new Vector3f();
        solarBodies[0].getPosition().sub(cameraLoc,front);
        vMat.identity();
        vMat.lookAlong(-cameraLoc.x()/cameraLoc.length(),-cameraLoc.y()/cameraLoc.length(),-cameraLoc.z()/cameraLoc.length(),0,1,0);
        vMat.translate(-cameraLoc.x(), -cameraLoc.y(), -cameraLoc.z());

        currentLightPos.set(solarBodies[0].getPosition());
        elapsedTime = System.currentTimeMillis() - prevTime;
        prevTime = System.currentTimeMillis();
        amt += elapsedTime * 0.001;

        installLights();

        for(int i = 0; i < solarBodies.length; i++){
            mMat.identity();
            if(i <1) {
                gl.glUniform1i(isSunLoc, 1);
                gl.glUniform1f(shininessLoc, 0);
            }else {
                gl.glUniform1i(isSunLoc, 0);
                gl.glUniform1f(shininessLoc, planets[i-1].specularComponent);
                SolarBody currOrbiting = planets[i-1].getParent();
                while(currOrbiting.getParent() != null){
                    mMat.rotateY((float) (amt * Math.PI * (2 / currOrbiting.orbitalPeriod())));
                    mMat.translate(currOrbiting.distFromCenter(), 0, 0);
                    currOrbiting = currOrbiting.getParent();
                }
                mMat.rotateY((float) (amt * Math.PI * (2 / planets[i - 1].orbitalPeriod)));
                mMat.translate(planets[i - 1].distFromCenter, 0, 0);
            }
            mMat.translate(SphereLoc.x(), SphereLoc.y(), SphereLoc.z());
            mMat.scale(solarBodies[i].getRadius());
            mMat.rotateY(-(float)(amt * Math.PI * (2/solarBodies[i].getRotationPeriod())));

            mMat.invert(invTrMat);
            invTrMat.transpose(invTrMat);

            gl.glUniformMatrix4fv(mLoc, 1, false, mMat.get(vals));
            gl.glUniformMatrix4fv(vLoc, 1, false, vMat.get(vals));
            gl.glUniformMatrix4fv(pLoc, 1, false, pMat.get(vals));
            gl.glUniformMatrix4fv(nLoc, 1, false, invTrMat.get(vals));

            gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
            gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
            gl.glEnableVertexAttribArray(0);

            gl.glActiveTexture(GL_TEXTURE0);
            gl.glBindTexture(GL_TEXTURE_2D, textureIDs[i]);

            gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
            gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
            gl.glEnableVertexAttribArray(1);

            gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
            gl.glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0);
            gl.glEnableVertexAttribArray(2);

            gl.glEnable(GL_CULL_FACE);
            gl.glFrontFace(GL_CCW);
            gl.glEnable(GL_DEPTH_TEST);
            gl.glDepthFunc(GL_LEQUAL);

            gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo[3]);
            gl.glDrawElements(GL_TRIANGLES, numSphereIndices, GL_UNSIGNED_INT, 0);
        }
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        aspect = (float) glCanvas.getWidth() / (float) glCanvas.getHeight();
        pMat.setPerspective((float) Math.toRadians(60.0f), aspect, 0.1f, 1000.0f);
    }

    private void installLights() {
        GL4 gl = (GL4) GLContext.getCurrentGL();

        lightPos[0]=solarBodies[0].getPosition().x(); lightPos[1]=solarBodies[0].getPosition().y(); lightPos[2]=solarBodies[0].getPosition().z();

        // get the locations of the light and material fields in the shader
        ambLoc = gl.glGetUniformLocation(renderingProgram, "light.ambient");
        diffLoc = gl.glGetUniformLocation(renderingProgram, "light.diffuse");
        specLoc = gl.glGetUniformLocation(renderingProgram, "light.specular");
        posLoc = gl.glGetUniformLocation(renderingProgram, "light.position");
        colorLoc = gl.glGetUniformLocation(renderingProgram, "color");
        linAttLoc = gl.glGetUniformLocation(renderingProgram, "linAtt");


        //  set the uniform light and material values in the shader
        gl.glProgramUniform4fv(renderingProgram, ambLoc, 1, lightAmbient, 0);
        gl.glProgramUniform4fv(renderingProgram, diffLoc, 1, lightDiffuse, 0);
        gl.glProgramUniform4fv(renderingProgram, specLoc, 1, lightSpecular, 0);
        gl.glProgramUniform3fv(renderingProgram, posLoc, 1, lightPos, 0);
        gl.glProgramUniform4fv(renderingProgram, colorLoc, 1, lightColor, 0);
        gl.glUniform1f(linAttLoc, linearAttenuationConstant);
    }

    private void setupVertices() {
        GL4 gl = (GL4) GLContext.getCurrentGL();

        mySphere = new Sphere(48);
        numSphereVertices = mySphere.getNumVertices();
        numSphereIndices = mySphere.getNumIndices();

        Vector3f[] vertices = mySphere.getVertices();
        Vector2f[] texCoords = mySphere.getTexCoords();
        Vector3f[] normals = mySphere.getNormals();
        int[] indices = mySphere.getIndices();

        float[] pvalues = new float[vertices.length*3];
        float[] tvalues = new float[texCoords.length*2];
        float[] nvalues = new float[normals.length*3];

        for (int i=0; i<numSphereVertices; i++) {
            pvalues[i*3]   = (float) vertices[i].x();
            pvalues[i*3+1] = (float) vertices[i].y();
            pvalues[i*3+2] = (float) vertices[i].z();
            tvalues[i*2]   = (float) texCoords[i].x();
            tvalues[i*2+1] = (float) texCoords[i].y();
            nvalues[i*3]   = (float) normals[i].x();
            nvalues[i*3+1] = (float) normals[i].y();
            nvalues[i*3+2] = (float) normals[i].z();
        }

        gl.glGenVertexArrays(vao.length, vao, 0);
        gl.glBindVertexArray(vao[0]);
        gl.glGenBuffers(4, vbo, 0);

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(pvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, vertBuf.limit()*4, vertBuf, GL_STATIC_DRAW);

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        FloatBuffer texBuf = Buffers.newDirectFloatBuffer(tvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, texBuf.limit()*4, texBuf, GL_STATIC_DRAW);

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
        FloatBuffer norBuf = Buffers.newDirectFloatBuffer(nvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, norBuf.limit()*4, norBuf, GL_STATIC_DRAW);

        gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo[3]);
        IntBuffer idxBuf = Buffers.newDirectIntBuffer(indices);
        gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf.limit()*4, idxBuf, GL_STATIC_DRAW);
    }

    private interface SolarBody{
        public Vector3f getPosition();
        public void setPosition(Vector3f position);
        public float x();
        public float y();
        public float z();
        public String getTextureFile();
        public float getRotationPeriod();
        public float getRadius();
        public SolarBody getParent();
        public float distFromCenter();
        public float orbitalPeriod();
    }
    private static class Sun implements SolarBody {
        public String textureFile;
        public float radius;
        public float rotationPeriod;
        public Vector3f position;


        //constructor for sun
        public Sun(String textureFile, float radius, float rotationPeriod) {
            this.textureFile = textureFile;
            this.radius = radius;
            this.rotationPeriod = rotationPeriod;
            this.position = new Vector3f();
        }

        @Override
        public Vector3f getPosition() {
            return position;
        }

        @Override
        public float getRotationPeriod() {
            return rotationPeriod;
        }

        @Override
        public void setPosition(Vector3f position) {
            this.position = position;
        }
        public String toString(){
            return "textureFile: " + textureFile + "\nradius: " + radius + "\nrotation period: " + rotationPeriod;
        }
        public float x(){
            return position.x();
        }
        public float y(){
            return position.y();
        }
        public float z(){
            return position.z();
        }
        public String getTextureFile(){
            return textureFile;
        }
        public float getRadius(){
            return radius;
        }
        public SolarBody getParent(){
            return null;
        }
        public float distFromCenter(){
            return 0;
        }
        public float orbitalPeriod(){
            return 0;
        }
    }

    private static class Planet implements SolarBody{
        public String textureFile;
        public float radius;
        public float rotationPeriod;
        public SolarBody orbiting;
        public float distFromCenter;
        public float orbitalPeriod;
        public float specularComponent;
        public Vector3f position;
        public float angle;

        public Planet(String textureFile, float radius, float rotationPeriod,float distFromCenter, float orbitalPeriod,float specularComponent,SolarBody orbiting){
            this.textureFile = textureFile;
            this.radius = radius;
            this.rotationPeriod = rotationPeriod;
            this.orbiting = orbiting;
            this.distFromCenter = distFromCenter;
            this.orbitalPeriod = orbitalPeriod;
            this.specularComponent = specularComponent;
            this.position = new Vector3f();
        }
        public String toString(){
            return "textureFile: " + textureFile
                    + "\nradius: " + radius
                    + "\nrotation period: " + rotationPeriod
                    + "\ndistance from center: " + distFromCenter
                    + "\norbital period: " + orbitalPeriod
                    + "\nspecular: " + specularComponent;
        }

        @Override
        public Vector3f getPosition() {
            return position;
        }

        @Override
        public float getRotationPeriod() {
            return rotationPeriod;
        }

        @Override
        public void setPosition(Vector3f position) {
            this.position = position;
        }
        public float getAngle(){
            return angle;
        }
        public void setAngle(float angle){
            this.angle = angle;
        }
        public float x(){
            return position.x();
        }
        public float y(){
            return position.y();
        }
        public float z(){
            return position.z();
        }
        public String getTextureFile(){
            return textureFile;
        }
        public float getRadius(){
            return radius;
        }
        public SolarBody getParent(){
            return orbiting;
        }
        public float distFromCenter(){
            return distFromCenter;
        }
        public float orbitalPeriod(){
            return orbitalPeriod;
        }
    }
}