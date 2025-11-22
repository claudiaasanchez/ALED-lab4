package es.upm.dit.aled.lab4.genome;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reads a FASTA file containing genetic information and allows for the search
 * of specific patterns within these data. The information is stored as an array
 * of bytes that contain nucleotides in the FASTA format. Since this array is
 * usually created before knowing how many characters in the origin FASTA file
 * are valid, an int indicating how many bytes of the array are valid is also
 * stored. All valid characters will be at the beginning of the array.
 * 
 * @author mmiguel, rgarciacarmona
 *
 */
/*FASTAReaderThreads: una clase que ejecutará tantas tareas FASTASearchCallable como
procesadores tenga el equipo, indicando a cada una sobre qué parte del genoma debe buscar.
Según estas vayan terminando, FASTAReaderThreads irá agregando los resultados en un
único conjunto, que será el que devuelva. Esta clase reutiliza gran parte del código de la
clase FASTASearch de la práctica 3 (todo menos los métodos search() y compare().*/

public class FASTAReaderThreads {

	// All threads can access the same content and valid bytes since they are never
	// modified after the file is loaded.
	protected byte[] content;
	protected int validBytes;

	/**
	 * Creates a new FASTAReader from a FASTA file.
	 * 
	 * @param fileName The name of the FASTA file.
	 */
	public FASTAReaderThreads(String fileName) {
		try {
			this.readFile(fileName);
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return;
		}
	}

	/*
	 * Helper method to read from a file. It populates the data array with upper
	 * case version of all the nucleotids found in the file. Throws an IOException
	 * if there is a problem accessing the file or the file is to big to fit in an
	 * array.
	 */
	private void readFile(String fileName) throws IOException {
		File f = new File(fileName);
		FileInputStream fis = new FileInputStream(f);
		DataInput fid = new DataInputStream(fis);
		long len = (int) fis.getChannel().size();
		if (len > Integer.MAX_VALUE) {
			fis.close();
			throw new IOException("The file " + fileName + " is too big. Can't be contained in an array.");
		}
		byte[] content = new byte[(int) len];
		int bytesRead = 0;
		int numRead = 0;
		String line;
		while ((line = fid.readLine()) != null) {
			// Put every character in upper case
			line = line.toUpperCase();
			numRead = line.length();
			byte[] newData = line.getBytes();
			for (int i = 0; i < numRead; i++)
				content[bytesRead + i] = newData[i];
			bytesRead += numRead;
		}
		fis.close();
		this.content = content;
		this.validBytes = bytesRead;
	}

	/**
	 * Provides the data array that contains the complete sequence of nucleotids
	 * extracted from the FASTA file.
	 * 
	 * @return The data array with each nucleotid taking one byte.
	 */
	public byte[] getContent() {
		return content;
	}

	/**
	 * Provides the amount of bytes in the data array that are valid. Since this
	 * array is created before the amount of bytes in the FASTA file that contain
	 * actual nucleotids are know, a worst-case scenario is assumed. So, only
	 * positions between content[0] and content[validBytes -1] have actual genomic
	 * data.
	 * 
	 * @return The number of valid bytes.
	 */
	public int getValidBytes() {
		return validBytes;
	}

	/**
	 * Performs a linear search to look for the provided pattern in the data array.
	 * To do this, it creates a thread pool with as many threads as cores has the
	 * computer this code is running on. Each of these threads will perform a linear
	 * search on the same byte[] (content), but only in a space 1/(number of cores)
	 * the size of the genome, so the work is split equally between all threads.
	 * When all threads have finished, it aggregates the results. Returns a List of
	 * Integers that point to the initial positions of all the occurrences of the
	 * pattern in the data.
	 * 
	 * @param pattern The pattern to be found.
	 * @return All the positions of the first character of every occurrence of the
	 *         pattern in the data.
	 */
	public List<Integer> search(byte[] pattern) {
		// TODO
		//Creo lista que devuelvo
		List<Integer> results = new ArrayList<Integer>(0);
		try {
			//creo exc que organice tantos threads como cores
			int cores = Runtime.getRuntime().availableProcessors();
			ExecutorService exc = Executors.newFixedThreadPool(cores);
			//creo tantas tareas como cores, con array
			Future<List<Integer>>[] futures = new Future[cores];
			//creo lo y hi, que serán los límites de busqueda lineal de cada thread
			//cada thread funciona en este tamaño:
			int tamano = content.length / cores;
			int lo = 0;
			int hi = 0 + tamano;
			// creo tantas tareas como cores y submit
			for (int i = 0; i < cores; i++) {
				Callable<List<Integer>> task = new FASTASearchCallable(this, lo, hi, pattern);
				Future<List<Integer>> future = exc.submit(task);
				futures[i] = future;
				// Aumento lo y hi
				lo += tamano;
				hi += tamano;
			}
			// Espero aque terminen las tareas con .get()
			for (int i = 0; i < futures.length; i++) {
				results.addAll(futures[i].get());
			}
			//acabo con el exc
			exc.shutdown();
		}catch(Exception e) {
			System.out.println("Tarea interrumpida"+ e.getMessage());
		}
		return results;
	}

	public static void main(String[] args) {
		long t1 = System.nanoTime();
		FASTAReaderThreads reader = new FASTAReaderThreads(args[0]);
		if (args.length == 1)
			return;
		System.out.println("Tiempo de apertura de fichero: " + (System.nanoTime() - t1));
		long t2 = System.nanoTime();
		List<Integer> posiciones = reader.search(args[1].getBytes());
		System.out.println("Tiempo de búsqueda: " + (System.nanoTime() - t2));
		if (posiciones.size() > 0) {
			for (Integer pos : posiciones)
				System.out.println("Encontrado " + args[1] + " en " + pos);
		} else
			System.out.println("No he encontrado : " + args[1] + " en ningun sitio");
		System.out.println("Tiempo total: " + (System.nanoTime() - t1));
	}
}
