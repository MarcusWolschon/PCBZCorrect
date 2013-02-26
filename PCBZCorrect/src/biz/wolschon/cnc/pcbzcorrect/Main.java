/**
 * 
 */
package biz.wolschon.cnc.pcbzcorrect;

import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * @author marcuswolschon
 *
 */
public class Main {

	private static final String UNIT_INCH = "Inch";
	private static final String UNIT_MM = "mm";
	private static String unit = null;
	private static final NumberFormat format = new DecimalFormat("###.#####",  new DecimalFormatSymbols(Locale.US));  
	public static void main(String[] args) {
		boolean graphical = false;
		// parse arguments
		if (args.length == 0) {
			System.out.println("input: g-code for milling a PCB");
			System.out.println("program asks for Z-height of PCB at different points");
			System.out.println("output: g-code for milling a PCB with z=0 being the surface of the uneven/warped PCB");
			System.out.println("usage: java -jar pcbzcorrect <in.gcode>");
			JFileChooser chooser = new JFileChooser();
			if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
				return;
			} else {
				args = new String[] {chooser.getSelectedFile().getAbsolutePath()};
				graphical = true;
			}
		}
		selftest();

		// read dimensions and unit
		File infile = new File(args[0]);
		System.out.println("determining dimensions of " + infile.getName() + "...");
		Rectangle2D max  = null;
		try {
		    max = getMaxDimensions(infile);
		} catch (Exception e) {
			System.err.println("cannot determine maximum dimensions");
			PrintWriter out = new PrintWriter(new OutputStreamWriter(System.err));
			e.printStackTrace(out);
			out.flush();
			out.close();
			return;
		}
		System.out.println("dimensions: "
		+ "(" + max.getMinX() + "," + max.getMinY() + unit + ") - "
		+ "(" + max.getMaxX() + "," + max.getMaxY() + unit + ")"
		+ "(width=" + max.getWidth() + ", height=" + max.getHeight() + unit + ")");

		double maxdist = distance(max.getMinX(), max.getMinY(), max.getMaxX(), max.getMaxY()) / 6;

		// ask for Z-probe at different points
		final int xsteps = 3;
		final int ysteps = 3;
		System.out.println("Using " + xsteps + " x " +ysteps + " z probe values");
		if (unit != null && unit.equals(UNIT_INCH)) {
			System.out.println("set unit to INCH using: G20");
			if (graphical) {
				JOptionPane.showMessageDialog(null, "set unit to INCH using: G20");
			}
		} else if (unit != null && unit.equals(UNIT_INCH)) {
			System.out.println("set unit to MILLIMETER using: G21");
			if (graphical) {
				JOptionPane.showMessageDialog(null, "set unit to MILLIMETER using: G21");
			}
		} else {
			System.err.println("No unit found (G20 or G21) in g-code");
			unit = "";
		}
		final double[] z = new double[xsteps * ysteps];
		try {
			BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
			for (int xi = 0; xi < xsteps; xi++) {
				for (int yi = 0; yi < ysteps; yi++) {
					Double zValue = null;

					while (zValue == null) {
						String message = "Z probe result at:  G1 Z10 G1 X" + getXLocation(xi, xsteps, max) + " Y" + getYLocation(yi, ysteps, max) + " G31 Z-10F100  ";
						System.out.print(message);	
						try {
							if (graphical) {
								zValue = Double.parseDouble(JOptionPane.showInputDialog(message));
							} else {
								zValue = Double.parseDouble(inputReader.readLine());
							}
						} catch (NumberFormatException e) {
							System.err.println("Not a number in g-code format. Please use '.' as decimal point.");
						}
					}
					z[xi + xsteps*yi] = zValue;
				}
			}
		} catch (IOException e) {
			System.err.println("cannot read z probes from user");
			PrintWriter out = new PrintWriter(new OutputStreamWriter(System.err));
			e.printStackTrace(out);
			out.flush();
			out.close();
			return;
		}

		try {
			File outfile = new File(infile.getAbsolutePath() + "_zproved.ngc");
			if (outfile.exists()) {
				System.err.println("overwriting output file!");
				outfile.delete();
			}
			System.out.println("Modifying g-code. Output to " + outfile.getName() + "...");
			ModifyGCode(infile, outfile, z, max, xsteps, ysteps, maxdist);
		} catch (IOException e) {
			System.err.println("ccannot modif g-code");
			PrintWriter out = new PrintWriter(new OutputStreamWriter(System.err));
			e.printStackTrace(out);
			out.flush();
			out.close();
			if (graphical) {
				JOptionPane.showMessageDialog(null, "cannot modify g-code");
			}
			return;
		}
		
		System.out.println("done!");
		if (graphical) {
			JOptionPane.showMessageDialog(null, "done!");
		}
	}
	private static void assertEquals(double expected, double value) {
		final double epsilon = 0.01d;
		if (Math.abs(value - expected) > epsilon) {
			throw new IllegalStateException("self test failed! expected=" + expected + " value=" + value);
		}
	}
	private static void selftest() {
		Rectangle2D dimensions=  new Rectangle2D.Double(-1.0d, 2.0d, 2.0d, 4.0d);
		assertEquals(0.0d, getXLocation(1, 3, dimensions));
		assertEquals(4.0d, getYLocation(1, 3, dimensions));
		assertEquals(2.0d, getYLocation(0, 10, dimensions));
		assertEquals(6.0d, getYLocation(7, 8, dimensions));
		
		assertEquals(0.5d, linearInterpolateX(1, 1, 0.5d,
				new double[] {9.0, 9.0,9.0,
				              9.0, 0.0, 1.0,
				              9.0, 9.0, 9.0},
			    3, 3));
		assertEquals(1.0d, linearInterpolateX(2, 1, 0.0d,
				new double[] {9.0, 9.0,9.0,
				              9.0, 9.0, 1.0,
				              9.0, 9.0, 9.0},
			    3, 3));

		assertEquals(1.0d, linearInterpolateX(0, 0, 0.0d,
				new double[] {1.0, 9.0,9.0,
				              9.0, 9.0, 9.0,
				              9.0, 9.0, 9.0},
			    3, 3));
		

		assertEquals(1.0d, getInterpolatedZ(-1.0d, 2.0d, 
				new double[] {1.0, 9.0,9.0,
				              9.0, 9.0, 9.0,
				              9.0, 9.0, 9.0},
				              dimensions, 3, 3));
		assertEquals(1.5d, getInterpolatedZ(-1.0d, 3.0d, 
				new double[] {1.0, 9.0,9.0,
				              2.0, 9.0, 9.0,
				              9.0, 9.0, 9.0},
				              dimensions, 3, 3));
		System.out.println("self test passed");
	}

	private static double distance(final double x1, final double y1, final double x2, final double y2) {
		double xdist = x2 - x1;
		double ydist = y2 - y1;
		return Math.sqrt(xdist * xdist + ydist * ydist);
	}
	/**
	 * Read the g-code from infile.
	 * Modify all Z values by adding the linear interpolation from the matrix z.
	 * Add a modified Z value to any line with X or Y but no Z coordinate.
	 * TODO: Does NOT break up long movements into smaller segments to follow a Z curve
	 * @param infile file to red
	 * @param outfile file to write (will be overwritten)
	 * @param z xsteps * ysteps meassurements of z height
	 * @param max the physical dimensions
	 * @param xsteps number of meassurements taken along the X axis and stored in z
	 * @param ysteps number of meassurements taken along the Y axis and stored in z
	 * @param maxdistance break up movements of more then this distance
	 * @throws IOException
	 */
	private static void ModifyGCode(final File infile, final File outfile, double[] z,
			Rectangle2D max, final int xsteps, final int ysteps, final double maxdistance) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(infile));
		BufferedWriter out = new BufferedWriter(new FileWriter(outfile));
		String line = null;
		String newline = System.getProperty("line.separator");
		Double currentX = null; // we don't care about instanciating lots of Double classes
		Double currentY = null;
		Double oldX = null;
		Double oldY = null;
		double lastZ = Double.MAX_VALUE;
		while ((line = in.readLine()) != null) {
			StringTokenizer tokens = new StringTokenizer(line, " ", false);
			StringBuilder outline = new StringBuilder();
			boolean found = false;
			boolean foundZ = false;
			try {
				while (tokens.hasMoreTokens()) {
					String token = tokens.nextToken();
					if (token.startsWith("X")) {
						oldX = currentX;
						currentX = Double.parseDouble(token.substring(1));
						token = "X{0}";
						found = true;
						//break;
					} else if (token.startsWith("Y")) {
						oldY = currentY;
						currentY = Double.parseDouble(token.substring(1));
						token = "Y{1}";
						found = true;
						//break;
					} else if (token.startsWith("Z")) {
						lastZ = Double.parseDouble(token.substring(1));
						if (currentX == null || currentY == null) {
							if (lastZ < 0) {
								System.err.println("Code contains a Z value < 0 before the first X or Y value.");
								System.err.println("Writing unchanged Z value for this location");
							}
						} else {
							token = "Z{2}";
						}
						foundZ = true;
					}
					outline.append(token).append(' ');

					if (outline.length() > 100) {
						System.err.println("line too long: '" + outline.toString() + "'");
						System.exit(-2);
					}
				}

				if (oldX == null || oldY == null || !found || distance(currentX, currentY, oldX, oldY) < maxdistance) {
					// output without breaking this up
					writeGCodeLine(z, max, xsteps, ysteps, out, newline, currentX,
							currentY, lastZ, outline, found, foundZ);
				} else {
					// break up this line to stay below maxdistance
					int count = (int) Math.ceil(distance(currentX, currentY, oldX, oldY) / maxdistance);
					out.write("( BROKEN UP INTO " + count + " MOVEMENTS )");
					out.write(newline);
					double xdist = currentX - oldX;
					double ydist = currentY - oldY;
					for (int i = 1; i < count + 1; i++) {
						double xinterpolated = oldX + i * xdist/count;
						double yinterpolated = oldY + i * ydist/count;
						writeGCodeLine(z, max, xsteps, ysteps, out, newline, xinterpolated,
								yinterpolated, lastZ, outline, found, foundZ);
					}
				}
				
			} catch (NumberFormatException e) {
				// ignored
				e.printStackTrace();
			}
		}
		out.close();
		in.close();
		
	}
	private static void writeGCodeLine(double[] z, Rectangle2D max,
			final int xsteps, final int ysteps, BufferedWriter out,
			String newline, Double currentX, Double currentY, double lastZ,
			StringBuilder outline, boolean found, boolean foundZ)
			throws IOException {
		if (found || foundZ) {
			double changedZ = lastZ;
			String xstr = "";
			String ystr = "";
			if (currentX != null && currentY != null) {
				changedZ = lastZ + getInterpolatedZ(currentX, currentY, z, max, xsteps, ysteps);
				xstr = format.format(currentX);
				ystr = format.format(currentY);
			}
			String formated = MessageFormat.format(outline.toString(), xstr, ystr, format.format(changedZ));
System.out.println("formated '" + outline.toString() + "' + '" + xstr + "'/'" + ystr + "'/'" + format.format(changedZ) + "' = '" + formated + "'");
			out.write(formated);
		} else {
			out.write(outline.toString());
		}

		// write line
		//TODO if (found && distance(currentX, currentY, oldX, oldY) > maxDistance) {breakUpLine(line)} else {
		if (found && !foundZ) {
			double changedZ = lastZ + getInterpolatedZ(currentX, currentY, z, max, xsteps, ysteps);
			out.write("Z" + format.format(changedZ));
		}
		out.write(newline);
	}
	/**
	 * Get the bilinear interpolation of the z values.
	 * @param lastX physical location in x
	 * @param lastY physical location in y
	 * @param z the z meassurements
	 * @param max max+min physical dimensions
	 * @param xsteps number of meassurements taken along the X axis and stored in z
	 * @param ysteps number of meassurements taken along the Y axis and stored in z
	 * @return the height Z=0 should be at
	 */
	private static double getInterpolatedZ(double lastX, double lastY,
			double[] z, Rectangle2D max, final int xsteps, final int ysteps) {
		// bilinear interpolation
		double xlength = lastX - max.getMinX();
		double ylength = lastY - max.getMinY();
		double xstep = max.getWidth() / (xsteps - 1);
		double ystep = max.getHeight() / (ysteps - 1);

		if (xlength < 0) {
			throw new IllegalArgumentException("xlength(=" + xlength + "=lastX(" + lastX + ")-minX(" + max.getMinX() + ")) < 0");
		} else if (xlength > max.getWidth()) {
			throw new IllegalArgumentException("xlength(=" + xlength + "=lastX(" + lastX + ")-minX(" + max.getMinX() + ")) > width(=" + max.getWidth() + ")");
		}
		if (ylength < 0) {
			throw new IllegalArgumentException("ylength(=" + ylength + ") < 0");
		} else if (ylength > max.getHeight()) {
			throw new IllegalArgumentException("ylength(=" + ylength + ") > height");
		}
		
		int xindex = (int) Math.floor(xlength / xstep);
		double xfactor = (xlength - (xindex * xstep)) / xstep;
		int yindex = (int) Math.floor(ylength / ystep);
		double yfactor = (ylength - (yindex * ystep)) / ystep;

		if (xindex >= xsteps) {
			throw new IllegalArgumentException("xindex(=" + xindex + "="
					+ "floor(xlength=" + xlength + " / xstep=" + xstep + ")"
					+ ") >= xsteps(=" + xsteps + ")");
		}
//System.out.println("DEBUG: xindex=" + xindex);
//System.out.println("DEBUG: yindex=" + yindex);
//System.out.println("DEBUG: xfactor=" + xfactor);
//System.out.println("DEBUG: yfactor=" + yfactor);
		double x1 = linearInterpolateX(xindex, yindex, xfactor, z ,xsteps, ysteps);
		if (yindex == ysteps - 1) {
			return x1;
		}
		double x2 = linearInterpolateX(xindex, yindex + 1, xfactor, z ,xsteps, ysteps);

//System.out.println("DEBUG: x1=" + x1);
//System.out.println("DEBUG: x2=" + x2);
		if (yfactor < 0) {
			throw new IllegalArgumentException("yfactor < 0");
		}
		if (yfactor > 1) {
			throw new IllegalArgumentException("yfactor > 1");
		}
		
		return (x2 * yfactor) + (x1 * (1 - yfactor));
	}

	/**
	 * Calculate a linear interpolation in X.
	 * @param xindex interpolatte between the values at xindex and xindex+1
	 * @param yindex y index into z
	 * @param xfactor 0.0=use value at xindex, 1.0=use value at xindex+1
	 * @param z meassurements to interpolate
	 * @param xsteps number of meassurements taken along the X axis and stored in z
	 * @param ysteps number of meassurements taken along the Y axis and stored in z
	 * @return
	 */
	private static double linearInterpolateX(int xindex, int yindex, double xfactor, double[] z,
			int xsteps, int ysteps) {
		if (xfactor < 0) {
			throw new IllegalArgumentException("xfactor < 0");
		}
		if (xfactor > 1) {
			throw new IllegalArgumentException("xfactor > 1");
		}
		if (xindex >= xsteps) {
			throw new IllegalArgumentException("xindex(=" + xindex + ") >= xsteps(=" + xsteps + ")");
		}
		if (yindex >= ysteps) {
			throw new IllegalArgumentException("yindex(=" + yindex + ") >= ysteps(=" + ysteps + ")");
		}
		double left = z[xindex + yindex * xsteps];
		if (xindex == xsteps - 1) {
			return left;
		}
		double right = z[xindex + 1 + yindex * xsteps];
		return (right * xfactor) + (left * (1 - xfactor));
	}
	/**
	 * @param xindex
	 * @param xsteps
	 * @param dimensions
	 * @return the X location of xindex in {@link #unit};
	 */
	private static double getXLocation(final int xindex, final int xsteps, final Rectangle2D dimensions) {
		double stepLength = dimensions.getWidth() / (xsteps - 1);
		return dimensions.getMinX() + stepLength * xindex;
	}
	/**
	 * @param yindex
	 * @param ysteps
	 * @param dimensions
	 * @return the Y location of yindex in {@link #unit};
	 */
	private static double getYLocation(final int yindex, final int ysteps, final Rectangle2D dimensions) {
		double stepLength = dimensions.getHeight() / (ysteps - 1);
		return dimensions.getMinY() + stepLength * yindex;
	}

	/**
	 * Read the file and find the maximum and mimumum physical dimensions.
	 * Also sets {@link #unit} as a side effect.
	 * @param infile
	 * @return
	 * @throws IOException
	 */
	private static Rectangle2D getMaxDimensions(File infile) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(infile));
		String line = null;
		double maxX = Double.MIN_VALUE;
		double minX = Double.MAX_VALUE;
		double maxY = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE;
		while ((line = in.readLine()) != null) {
			// normalize
			line = line.toUpperCase();
			if (line.startsWith("G20")) {
				Main.unit = UNIT_INCH;
			}
			if (line.startsWith("G21")) {
				Main.unit = UNIT_MM;
			}
			StringTokenizer tokens = new StringTokenizer(line, " ", false);
			while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();
				try {
					if (token.startsWith("X")) {
						Double value = Double.parseDouble(token.substring(1));
						maxX = Math.max(maxX, value);
						minX = Math.min(minX, value);
					} else if (token.startsWith("Y")) {
						Double value = Double.parseDouble(token.substring(1));
						maxY = Math.max(maxY, value);
						minY = Math.min(minY, value);
					}
				} catch (NumberFormatException e) {
					// ignored
				}
			}
		}
		in.close();
		
		Rectangle2D max=  new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
		return max;
	}
}
