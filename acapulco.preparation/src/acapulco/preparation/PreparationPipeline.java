package acapulco.preparation;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.uma.mo_dagame.feature_models.values_generator.ValuesGenerator;

import acapulco.preparation.cleaning.Cleaner;
import acapulco.preparation.cleaning.ComplexConstraintRemover;
import acapulco.preparation.converters.MainModagameToSatibea;
import acapulco.preparation.converters.MainSatibeaSeedToModagameSeed;
import acapulco.preparation.converters.RandomRichSee;

public class PreparationPipeline {
	private static PrintStream _err;

	public static void main(String[] args) throws IOException {
		// Assumes an uncleaned sxfm with complex constraints
		String pathPrefix = "C:\\Users\\danstru\\git\\";
		String directoryInput = "input";
		String fmNameInput = "linux-2.6.33.3.sxfm.xml";
		String caseName = "linux";

		generateAllFromFm(directoryInput, fmNameInput, caseName, pathPrefix);

	}

	public static void generateAllFromFm(String directoryInput, String fmNameInput, String caseName, String pathPrefix)
			throws IOException {

		fmNameInput += ".sxfm";
		String qaNameInput = "UsabilityBatteryMemory.sample";
		String pathOutput = pathPrefix + "\\";

		System.out.println("[Start cleaning.] ");
		closeSystemErr();
		// Clean
		String[] input = new String[1];
		input[0] = directoryInput + "\\" + fmNameInput + ".xml";
		ComplexConstraintRemover.main(input);
		openSystemErr();

		input[0] = directoryInput + "\\" + fmNameInput + ".xml-nocomplex.sxfm.xml";
		Cleaner.main(input);
		input[0] = directoryInput + "\\" + fmNameInput + ".xml-onlycomplex.sxfm.xml";
		Cleaner.main(input);
		System.out.println("[Done cleaning.] ");
		
		System.out.println("[Start attribute value generation.] ");
		// Call Values Generator -> obj

		// Generate obj without header (MODAGAME) and with header (MDEO)
		String pathCleaned = directoryInput + "\\" + fmNameInput + ".xml-nocomplex.sxfm.xml.clean.sxfm.xml";
		input = new String[3];
		input[0] = pathCleaned;
		input[1] = directoryInput + "\\" + qaNameInput;
		input[2] = null;
		ValuesGenerator.main(input);

		System.out.println("[End attribute value generation.] ");

		System.out.println("[Begin SATIBEA input generation.] ");
		// Call EMFConverter -> dimacs + augment + dead + mandatory
		MainModagameToSatibea.execute(directoryInput + "\\", fmNameInput, directoryInput + "\\");

		// call SATIBEA_richseedGenerator
		input = new String[3];
		input[0] = fmNameInput + ".xml-nocomplex.sxfm.xml.clean.sxfm";
		input[1] = directoryInput + "\\";
		input[2] = directoryInput + "\\";
		RandomRichSee.main(input);

		// Call EMFConverter -> csv for Modagame
		MainSatibeaSeedToModagameSeed.main(input);
		System.out.println("[End SATIBEA input generation.] ");

		// Copy files
		new File(pathOutput + "\\" + caseName).mkdirs();
		new File(pathOutput + "\\" + caseName + "\\acapulco").mkdirs();
		new File(pathOutput + "\\" + caseName + "\\modagame").mkdirs();
		new File(pathOutput + "\\" + caseName + "\\satibea").mkdirs();
		String baseInputPath = directoryInput + "\\" + fmNameInput + ".xml-nocomplex.sxfm.xml.clean.sxfm";
		String baseInputPathComplex = directoryInput + "\\" + fmNameInput + ".xml-onlycomplex.sxfm.xml.clean.sxfm";
		
		System.out.println("** Done with file generation. **");

		// Copy Acapulco files
		String baseOutputPath = pathOutput + "\\" + caseName + "\\acapulco\\";
		Files.copy(new File(baseInputPath + ".dimacs").toPath(),
				new File(baseOutputPath + caseName + ".dimacs").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPathComplex + ".dimacs").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.complex").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.augment").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.augment").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.richseed").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.richseed").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.mandatory").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.mandatory").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.dead").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.dead").toPath(), StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Done copying Acapulco files");
		
		// Copy Modagame files
		baseOutputPath = pathOutput + "\\" + caseName + "\\modagame\\" + caseName;
		System.out.println(baseOutputPath);
		new File(baseOutputPath).mkdirs();
		new File(baseOutputPath + "\\models\\").mkdirs();
		new File(baseOutputPath + "\\seeds\\").mkdirs();
		System.out.println(baseInputPath + ".xml");
		Files.copy(new File(baseInputPath + ".xml").toPath(), new File(baseOutputPath + "\\models\\" + caseName + ".xml").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".obj").toPath(), new File(baseOutputPath + "\\models\\" + caseName + ".obj").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".csv").toPath(), new File(baseOutputPath + "\\seeds\\" + caseName + ".csv").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Done copying Modagame files");
		
		// Copy Satibea files
		baseOutputPath = pathOutput + "\\" + caseName + "\\satibea\\";
		Files.copy(new File(baseInputPath + ".dimacs").toPath(),
				new File(baseOutputPath + caseName + ".dimacs").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.augment").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.augment").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.dead").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.dead").toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.mandatory").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.mandatory").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(baseInputPath + ".dimacs.richseed").toPath(),
				new File(baseOutputPath + caseName + ".dimacs.richseed").toPath(), StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Done copying Satibea files");
		
		// Cleanup: delete generated files from temporary directory
		final String fmNameInputFinal = fmNameInput;
		String[] generatedFiles = new File(directoryInput).list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(fmNameInputFinal) && !name.equals(fmNameInputFinal + ".xml");
			}
		});
		for (String file : generatedFiles) {
			new File(directoryInput + "/" + file).delete();
			System.out.println("Deleted aux file: " + file);
		}

		System.out.println("** Done with copying. **");
	}

	private static void closeSystemErr() {
		_err = System.err;
		System.setErr(new PrintStream(new OutputStream() {
			public void write(int b) {
			}
		}));
	}

	private static void openSystemErr() {
		System.setErr(_err);
	}

}
