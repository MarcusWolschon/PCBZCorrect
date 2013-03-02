/**
 * 
 */
package biz.wolschon.cnc.pcbzcorrect;

import java.awt.HeadlessException;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * @author marcuswolschon
 *
 */
public class Main {

	/**
	 * Index of the first g-code variable we're using to store our Z-meassurements.
	 */
	private static final int STARTVARRANGE = 100;
	private static final String UNIT_INCH = "Inch";
	private static final String UNIT_MM = "mm";
	private static String unit = null;
	/**
	 * how to format numbers in g-code
	 */
	private static final NumberFormat format = new DecimalFormat("###.#####",  new DecimalFormatSymbols(Locale.US));  
	/**
	 * for graphical logging
	 */
	private static JTextArea textarea;
	/**
	 * create code for MACH3 or EMC2
	 */
	private static boolean mach3 = true;
	public static void main(String[] args) {
		boolean graphical = false;
		// parse arguments
		try {
			if (args.length == 0) {
				System.out.println("input: g-code for milling a PCB");
				System.out.println("program asks for Z-height of PCB at different points");
				System.out.println("output: g-code for milling a PCB with z=0 being the surface of the uneven/warped PCB");
				System.out.println("usage: java -jar pcbzcorrect <in.gcode>");
				JFileChooser chooser = new JFileChooser();
				if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
					JOptionPane.showMessageDialog(null, "aborted!");
					return;
				} else {
					args = new String[] {chooser.getSelectedFile().getAbsolutePath()};
					graphical = true;
					JDialog dlg = new JDialog();
					textarea = new JTextArea();
					JScrollPane scroller = new JScrollPane(textarea);
					dlg.setContentPane(scroller);
					dlg.pack();
					dlg.setVisible(true);
				}
			}
			
			// read dimensions and unit
			File infile = new File(args[0]);
			log("determining dimensions of " + infile.getName() + "...");
			Rectangle2D max  = null;
			try {
			    max = getMaxDimensions(infile);
			} catch (Exception e) {
				logError("cannot determine maximum dimensions");
				PrintWriter out = new PrintWriter(new OutputStreamWriter(System.err));
				e.printStackTrace(out);
				out.flush();
				out.close();
				if (graphical) {
					JOptionPane.showMessageDialog(null, "cannot determine maximum dimensions [" + e.getClass().getName() + "] " + e.getMessage());
				}
				return;
			}
			String msg = "dimensions: "
			+ "(" + max.getMinX() + "," + max.getMinY() + unit + ") - "
			+ "(" + max.getMaxX() + "," + max.getMaxY() + unit + ")"
			+ "(width=" + max.getWidth() + ", height=" + max.getHeight() + unit + ")";
			log(msg);
			
			File outfile = new File(infile.getAbsolutePath() + "_zprobed.ngc");
			if (outfile.exists()) {
				log("overwriting output file!");
				outfile.delete();
			}
			log("Modifying g-code. Output to " + outfile.getName() + "...");
			BufferedWriter out;
			try {
				out = new BufferedWriter(new FileWriter(outfile));
			} catch (IOException e1) {
				logError("cannot open output file " + outfile.getAbsolutePath());
				StringWriter sw = new StringWriter();
				PrintWriter exout = new PrintWriter(sw);
				e1.printStackTrace(exout);
				exout.flush();
				exout.close();
				logError(sw.toString());
				return;
			}
			String newline = System.getProperty("line.separator");
			
			double maxdist = distance(max.getMinX(), max.getMinY(), max.getMaxX(), max.getMaxY()) / 6;

			// write subprogram

			// ask for Z-probe at different points
			int xsteps;
			int ysteps;
			try {
				out.write("(Things you can change:)");out.write(newline);
				if (unit != null && unit.equals(UNIT_MM)) {
					out.write("#1=50		(Safe height)");out.write(newline);
					out.write("#2=10		(Travel height)");out.write(newline);
					out.write("#3=-.004		(Route depth)");out.write(newline);
				    out.write("#4=-10		(Probe depth)");out.write(newline);
				    out.write("");out.write(newline);
					out.write("(Things you should not change:)");out.write(newline);
					out.write("G21		(mm)");out.write(newline);
				} else {
					// sadly for PCBs we have to default to imperial **** inches
					out.write("#1=12		(Safe height)");out.write(newline);
					out.write("#2=1		    (Travel height)");out.write(newline);
					out.write("#3=-.1		(Route depth)");out.write(newline);
				    out.write("#4=-1		(Probe depth)");out.write(newline);
				    out.write("");out.write(newline);
					out.write("(Things you should not change:)");out.write(newline);
					out.write("G20		(inch)");out.write(newline);
				}
				out.write("G90		(Abs coords)");out.write(newline);
				out.write("");out.write(newline);
				out.write("M05		(Stop Motor)");out.write(newline);
				out.write("G00 Z[#1]       (Safe height)");out.write(newline);
				out.write("G00 X0 Y0       (.. on the ranch)");out.write(newline);
				out.write("");out.write(newline);
				
				xsteps = 3;
				ysteps = 3;
					for (int xi = 0; xi < xsteps; xi++) {
						for (int yi = 0; yi < ysteps; yi++) {
							int arrayIndex = STARTVARRANGE + xi + xsteps*yi;

							double xLocation = getXLocation(xi, xsteps, max);
							double yLocation = getYLocation(yi, ysteps, max);
							out.write("(PROBE[" + xi + "," + yi + "] " + format.format(xLocation) + " " + format.format(yLocation) + " -> " + arrayIndex + ")");out.write(newline);
							out.write("G00 X" + format.format(xLocation) + " Y" + format.format(yLocation) + " Z[#2]");out.write(newline); //#2=travel high
							if (mach3 ) { //MACH3
								out.write("G31 Z[#4] F25");out.write(newline); // #4 = probe depth
								out.write("#" + arrayIndex + "=#2002");out.write(newline); //#2000=X, #2001=Y, #2002=Z
							} else { // EMC2
								out.write("G38.2 Z[#4] F25");out.write(newline); // #4 = probe depth
								out.write("#" + arrayIndex + "=#5063");out.write(newline);
							}
							out.write("G00 Z[#2]");out.write(newline);//#2=travel high

						}
					}
			out.write("( PROBING DONE, remove probe now, then press CYCLE START)");out.write(newline);//#2=travel high
			out.write("M0");out.write(newline);//#2=travel high
			

			} catch (IOException e1) {
				logError("cannot write header for g-code");
				StringWriter sw = new StringWriter();
				PrintWriter exout = new PrintWriter(sw);
				e1.printStackTrace(exout);
				exout.flush();
				exout.close();
				logError(sw.toString());
				return;
			}
			

			try {
				ModifyGCode(infile, out, max, xsteps, ysteps, maxdist);
			} catch (IOException e) {
				logError("cannot modify g-code");
				StringWriter sw = new StringWriter();
				PrintWriter exout = new PrintWriter(sw);
				e.printStackTrace(exout);
				exout.flush();
				exout.close();
				logError(sw.toString());
				return;
			}
			
			log("done!");
			if (graphical) {
				JOptionPane.showMessageDialog(null, "done!");
			}
		} catch (HeadlessException e) {
			e.printStackTrace();
		}
	}
	private static void logError(final String message) {
		System.err.println(message);
		if (textarea != null) {
			textarea.setText("ERROR: " + textarea.getText() + "\n" + message);
		}
	}
	private static void log(final String message) {
		System.out.println(message);
		if (textarea != null) {
			textarea.setText(textarea.getText() + "\n" + message);
		}
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
	 * @param infile file to red
	 * @param outfile file to write (will be overwritten)
	 * @param max the physical dimensions
	 * @param xsteps number of meassurements taken along the X axis and stored in z
	 * @param ysteps number of meassurements taken along the Y axis and stored in z
	 * @param maxdistance break up movements of more then this distance
	 * @throws IOException
	 */
	private static void ModifyGCode(final File infile, final BufferedWriter out,
			Rectangle2D max, final int xsteps, final int ysteps, final double maxdistance) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(infile));
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
								logError("Code contains a Z value < 0 before the first X or Y value.");
								logError("Writing unchanged Z value for this location");
							}
						} else {
							token = "Z{2}";
						}
						foundZ = true;
					}
					outline.append(token).append(' ');

					if (outline.length() > 100) {
						logError("line too long: '" + outline.toString() + "'");
						System.exit(-2);
					}
				}

				if (oldX == null || oldY == null || !found || distance(currentX, currentY, oldX, oldY) < maxdistance) {
					// output without breaking this up
					writeGCodeLine(max, xsteps, ysteps, out, newline, currentX,
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
						writeGCodeLine(max, xsteps, ysteps, out, newline, xinterpolated,
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
	private static void writeGCodeLine(Rectangle2D max,
			final int xsteps, final int ysteps, BufferedWriter out,
			String newline, Double currentX, Double currentY, double lastZ,
			StringBuilder outline, boolean found, boolean foundZ)
			throws IOException {
		if (found || foundZ) {
			String changedZ = format.format(lastZ);
			String xstr = "";
			String ystr = "";
			if (currentX != null && currentY != null) {
				changedZ = "[" + changedZ + " + " + getInterpolatedZ(currentX, currentY, max, xsteps, ysteps) + "]";
				xstr = format.format(currentX);
				ystr = format.format(currentY);
			}
			String formated = MessageFormat.format(outline.toString(), xstr, ystr, changedZ);
System.out.println("formated '" + outline.toString() + "' + '" + xstr + "'/'" + ystr + "'/'" + changedZ + "' = '" + formated + "'");
			out.write(formated);
		} else {
			out.write(outline.toString());
		}

		// write line
		if (found && !foundZ) {
			String changedZ = "[" + format.format(lastZ) + " + " + getInterpolatedZ(currentX, currentY, max, xsteps, ysteps)+ "]";
			out.write("Z" + changedZ);
		}
		out.write(newline);
	}
	/**
	 * Get the bilinear interpolation of the z values.
	 * @param lastX physical location in x
	 * @param lastY physical location in y
	 * @param max max+min physical dimensions
	 * @param xsteps number of meassurements taken along the X axis and stored in z
	 * @param ysteps number of meassurements taken along the Y axis and stored in z
	 * @return the height Z=0 should be at
	 */
	private static String getInterpolatedZ(double lastX, double lastY,
			Rectangle2D max, final int xsteps, final int ysteps) {
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
		if (yindex == ysteps - 1) {
			String x1 = linearInterpolateX(xindex, yindex, xfactor, 1.0d ,xsteps, ysteps);
		   return x1;
		}

//System.out.println("DEBUG: x1=" + x1);
//System.out.println("DEBUG: x2=" + x2);
		if (yfactor < 0) {
			throw new IllegalArgumentException("yfactor < 0");
		}
		if (yfactor > 1) {
			throw new IllegalArgumentException("yfactor > 1");
		}
		String x1 = linearInterpolateX(xindex, yindex, xfactor, 1-yfactor ,xsteps, ysteps);
		String x2 = linearInterpolateX(xindex, yindex + 1, xfactor, yfactor ,xsteps, ysteps);
		
		return x1 + " + " + x2;
	}

	/**
	 * Calculate a linear interpolation in X.
	 * @param xindex interpolatte between the values at xindex and xindex+1
	 * @param yindex y index into z
	 * @param xfactor 0.0=use value at xindex, 1.0=use value at xindex+1
	 * @param yFactor multiply this to our own internal factor for the register value
	 * @param xsteps number of meassurements taken along the X axis and stored in z
	 * @param ysteps number of meassurements taken along the Y axis and stored in z
	 * @return
	 */
	private static String linearInterpolateX(int xindex, int yindex, double xfactor, double yFactor,
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
		int leftIndex = STARTVARRANGE + xindex + yindex * xsteps;
		if (xindex == xsteps - 1) {
			return format.format(yFactor) + "*" + "#" + leftIndex;
		}
		int rightIndex = STARTVARRANGE + xindex + 1 + yindex * xsteps;
		
		return format.format(xfactor * yFactor) + " * " + "#" + rightIndex
			+ " + "
			+ format.format((1 - xfactor) * yFactor) + " * " + "#" + leftIndex;
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
