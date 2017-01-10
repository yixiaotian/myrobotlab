/**
 * 
 */
package org.myrobotlab.kinematics;

import java.util.HashMap;

import org.myrobotlab.math.MathUtils;
import org.myrobotlab.openni.OpenNiData;
import org.myrobotlab.openni.PVector;

/**
 * 
 * This class register the 3d environement detected by the kinect sensor
 * @author Christian
 *
 */
public class Map3D {

	public enum CoordStateValue {
		UNDEFINED,
		EMPTY,
		FILL;
	}
	static double fx_d = 1.0 / 5.9421434211923247e+02;
	static double fy_d = 1.0 / 5.9104053696870778e+02;
	static double cx_d = 3.3930780975300314e+02;
	static double cy_d = 2.4273913761751615e+02;
	
	public int widthImage = 640;
	public int heighImage = 480;
	public int maxDepthValue = 2000;
	public int closestDistance = 450;
	public int fartestDistance = 1500;
	
	public int skip = 10;
	HashMap<Integer,HashMap<Integer,HashMap<Integer,Map3DPoint>>> coordValue = new HashMap<Integer,HashMap<Integer,HashMap<Integer,Map3DPoint>>>();
	private Point kinectPosition;
	
	class Map3DPoint {
		CoordStateValue value = CoordStateValue.UNDEFINED;
		boolean usedForObject = false;
	}
	
	public Map3D() {
		
	}

	public void processDepthMap(OpenNiData data) {
		PVector[] depthData = data.depthMapRW;
		for (int x = 0; x < widthImage; x+=skip ) {
			for (int y = 0; y < heighImage; y+=skip) {
				int index = x + y*widthImage;
				PVector loc = null;
				if (depthData[index].z > closestDistance && depthData[index].z <= fartestDistance) {
					for (float z = closestDistance; z < depthData[index].z - skip; z+=(float)skip) {
						loc = PVector.div(depthData[index], depthData[index].z);
						loc = PVector.mult(loc, z);
						addCoordValue(loc.x, loc.y, loc.z, CoordStateValue.EMPTY);
					}
					addCoordValue(depthData[index].x, depthData[index].y, depthData[index].z, CoordStateValue.FILL);
					for (float z = depthData[index].z + (float)skip; z < fartestDistance; z+=(float)skip) {
						loc = PVector.div(depthData[index], depthData[index].z);
						loc = PVector.mult(loc, z);
						addCoordValue(loc.x, loc.y, loc.z, CoordStateValue.UNDEFINED);
					}
				}
				if (depthData[index].z > fartestDistance) {
					for (float z = closestDistance; z <= depthData[index].z && z <= fartestDistance; z+=(float)skip) {
						loc = PVector.div(depthData[index], depthData[index].z);
						loc = PVector.mult(loc, z);
						addCoordValue(loc.x, loc.y, loc.z, CoordStateValue.EMPTY);
					}
				}
			}
		}
	}


	private void addCoordValue(double xpos, double ypos, double zpos, CoordStateValue value) {
		addCoordValue((int)xpos, (int)ypos, (int)zpos, value);
	}
	
	private void addCoordValue(int xpos, int ypos, int zpos, CoordStateValue value) {
		//need to rotate and translate the location depending on the position of the kinect
		//rotate
    double roll = MathUtils.degToRad(kinectPosition.getRoll());
    double pitch = MathUtils.degToRad(kinectPosition.getPitch());
    double yaw = MathUtils.degToRad(kinectPosition.getYaw());
    Matrix trMatrix = Matrix.translation(kinectPosition.getX(), kinectPosition.getY(), kinectPosition.getZ());
    Matrix rotMatrix = Matrix.xRotation(roll).multiply(Matrix.yRotation(pitch).multiply(Matrix.zRotation(yaw)));
    Matrix coord = Matrix.translation(xpos, zpos, ypos);
    Matrix inputMatrix = trMatrix.multiply(rotMatrix).multiply(coord);

    Point pOut = new Point(inputMatrix.elements[0][3], inputMatrix.elements[1][3], inputMatrix.elements[2][3], 0, 0, 0);
		
		//translate
//		double posx = xpos + kinectPosition.getX();
//		double posy = zpos + kinectPosition.getY();
//		double posz = ypos + kinectPosition.getZ();
		//convert to the coordinate use by our ik engine and reduce the resolution
		double posx = (int)((int)pOut.getX()/skip*skip);
		double posy = (int)((int)pOut.getY()/skip*skip);
		double posz = (int)((int)pOut.getZ()/skip*skip);
		HashMap<Integer,HashMap<Integer,Map3DPoint>> y = coordValue.get((int)posx);
		if (y == null) {
			y = new HashMap<Integer,HashMap<Integer,Map3DPoint>>();
		}
		HashMap<Integer,Map3DPoint> z = y.get((int)posy);
		if (z == null) {
			z = new HashMap<Integer,Map3DPoint>();
		}
		switch (value){
			case EMPTY:
				if (z.get((int)posz) != null) {
					z.remove((int)posz);
				}
				break;
			case FILL: {
				Map3DPoint o = new Map3DPoint();
				o.value = value;
				z.put((int)posz, o);
				break;
			}
			case UNDEFINED:{
				if (z.get((int)posz) == null){
					Map3DPoint o = new Map3DPoint();
					o.value = value;
					z.put((int)posz, o);
				}
				break;
			}
		}
		y.put((int)posy, z);
		coordValue.put((int)posx, y);
	}
	
	public CoordStateValue getCoordValue(double xpos, double ypos, double zpos) {
		return getCoordValue((int)xpos, (int)ypos, (int)zpos);
	}
	
	public CoordStateValue getCoordValue(int xpos, int ypos, int zpos) {
		int posx = xpos/skip*skip;
		int posy = ypos/skip*skip;
		int posz = xpos/skip*skip;
		HashMap<Integer,HashMap<Integer,Map3DPoint>> y = coordValue.get(posx);
		if (y == null) {
			return CoordStateValue.EMPTY;
		}
		HashMap<Integer,Map3DPoint> z = y.get(posy);
		if (z == null) {
			return CoordStateValue.EMPTY;
		}
		return CoordStateValue.FILL; //return FILL even if it's undefined (we don't know if it's fill or not, better not go.
		//return z.get(posz);
	}

	public void updateKinectPosition(Point currentPosition) {
		kinectPosition = currentPosition;
	}
}
