package com.coryjuhlin.PoCoTool;

/* This application requires the ASM 4.2 library for class file analysis.
 * You can download it from http://asm.ow2.org
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.objectweb.asm.ClassReader;

public class RegexScanner implements ActionListener, ListSelectionListener, ItemListener {
	public static final boolean DEBUG_MODE = true;
	private static final int NUM_THREADS = 4;
	
	private final ExecutorService pool;
	
	private JFrame appframe;
	
	private JPanel fileSelectTabPanel;
	
	private JList<File> fileList;
	private DefaultListModel<File> filesToScan;
	
	private JPanel fileButtonPanel;
	private JButton addFileButton;
	private JButton removeFileButton;
	
	private JPanel regexPanel;
	private JTextField regexFileField;
	private JButton regexButton;
	
	private JButton generateButton;
	private JLabel generationTimeLabel;
	
	private JList<String> regexList;
	private JList<String> methodList;
	
	private JLabel numMethodsValueLabel;
	private JLabel numRegexValueLabel;
	
	private JFileChooser classFileChooser;
	private JFileChooser regexFileChooser;
	
	private JCheckBox runEliminatingPassCheckbox;
	private boolean runEliminatingPass = false;
	
	private JLabel selectedRegexMethodCountLabel;

	private LinkedHashMap<String, ArrayList<String>> generatedMappings;
	
	private File regexFile = null;
	
	private long generateStartTime = 0l;
	private long numMethods = 0l;
		
	public RegexScanner() {
		if(DEBUG_MODE) {
			System.out.println("Init, before UI init: " + Thread.currentThread().getId());
		}
		
		
		
		// Put UI Initialization on the Swing UI Event Thread
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if(DEBUG_MODE) {
					System.out.println("Init UI: " + Thread.currentThread().getId());
				}
				
				initializeUI();
			}
		});
		
		pool = Executors.newFixedThreadPool(NUM_THREADS);
	}
	
	public void itemStateChanged(ItemEvent e) {
		Object source = e.getItemSelectable();
		boolean selected = false;
		
		if(e.getStateChange() == ItemEvent.SELECTED) {
			selected = true;
		} else if(e.getStateChange() == ItemEvent.DESELECTED) {
			selected = false;
		}
		
		if(source == runEliminatingPassCheckbox) {
			runEliminatingPass = selected;
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		if(DEBUG_MODE) {
			System.out.println("actionPerformed: " + Thread.currentThread().getId());
		}
		
		if(e.getSource() == addFileButton) {
			int returnVal = classFileChooser.showOpenDialog(appframe);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				for(File file : classFileChooser.getSelectedFiles()) {
					filesToScan.addElement(file);
				}
			}
			
		} else if(e.getSource() == removeFileButton) {
			for(int i = fileList.getSelectedIndices().length - 1; i >= 0; i--) {
				filesToScan.remove(fileList.getSelectedIndices()[i]);
			}
		} else if(e.getSource() == regexButton) {
			int returnVal = regexFileChooser.showOpenDialog(appframe);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				regexFile = regexFileChooser.getSelectedFile();
				regexFileField.setText(regexFile.getName());
				regexFileField.setToolTipText(regexFile.getPath());
			}
		} else if(e.getSource() == generateButton) {
			generationTimeLabel.setText("Generating...");
			addFileButton.setEnabled(false);
			removeFileButton.setEnabled(false);
			removeFileButton.setEnabled(false);
			regexButton.setEnabled(false);
			generateButton.setEnabled(false);
			
			generateStartTime = System.nanoTime();
			
			File[] files = new File[filesToScan.size()];
			filesToScan.copyInto(files);
			
			(new JavaFileLoader(files, regexFile, runEliminatingPass)).execute();
		}
		
		if(filesToScan.size() > 0) {
			removeFileButton.setEnabled(true);
		} else {
			removeFileButton.setEnabled(false);
		}
	}
	
	public void valueChanged(ListSelectionEvent e) {
		if(e.getValueIsAdjusting() || regexList.isSelectionEmpty()) {
			return;
		}
		String expr = regexList.getSelectedValue();
		ArrayList<String> mappedMethods = generatedMappings.get(expr);
		methodList.setListData(mappedMethods.toArray(new String[0]));
		selectedRegexMethodCountLabel.setText("Count: " + mappedMethods.size());
	}
	
	public void generateComplete() {
		if(generatedMappings != null) {
			long endTime = System.nanoTime();
			long generationTime = endTime - generateStartTime;
			double millis = generationTime / 1000000d;
			
			String genTimeText = String.format("Generation time: %.2f ms", millis);
			generationTimeLabel.setText(genTimeText);
			
			regexList.setListData(generatedMappings.keySet().toArray(new String[0]));
			DecimalFormat formatter = new DecimalFormat("#,##0");
			numRegexValueLabel.setText(formatter.format(generatedMappings.size()));
			numMethodsValueLabel.setText(formatter.format(numMethods));
		} else {
			generationTimeLabel.setText(null);
		}
		
		addFileButton.setEnabled(true);
		removeFileButton.setEnabled(true);
		removeFileButton.setEnabled(true);
		regexButton.setEnabled(true);
		generateButton.setEnabled(true);
	}

	public void initializeUI() {
		// Set up application window
		appframe = new JFrame("PoCo Static Method Tool");
		appframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		appframe.setBounds(50, 50, 700, 600);
		appframe.setMinimumSize(new Dimension(600, 400));
		
		// Configure file choosers
		classFileChooser = new JFileChooser();
		classFileChooser.setDialogTitle("Add Class Files");
		classFileChooser.setApproveButtonText("Add");
		FileNameExtensionFilter fileFilter = new FileNameExtensionFilter("Compiled Java Classes",
				"class", "jar");
		classFileChooser.setFileFilter(fileFilter);
		classFileChooser.setMultiSelectionEnabled(true);
		classFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		
		regexFileChooser = new JFileChooser();
		regexFileChooser.setDialogTitle("Add Regular Expression File");
		regexFileChooser.setApproveButtonText("Add");
		FileNameExtensionFilter textFileFilter = new FileNameExtensionFilter("Text Files",
				"txt", "regex", "text", "poco");
		regexFileChooser.setFileFilter(textFileFilter);
		regexFileChooser.setMultiSelectionEnabled(false);
		regexFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		
		
		// Create panel for file selections
		SpringLayout fileSelectionLayout = new SpringLayout();
		fileSelectTabPanel = new JPanel(fileSelectionLayout);
		fileSelectTabPanel.setOpaque(false);
		
		filesToScan = new DefaultListModel<>();
		
		fileList = new JList<>(filesToScan);
		fileList.setVisibleRowCount(10);
		fileList.setCellRenderer(new FileRenderer());
		
		JScrollPane fileListScroller = new JScrollPane(fileList);
		
		addFileButton = new JButton("Add File");
		removeFileButton = new JButton("Remove File");
		removeFileButton.setEnabled(false);
		
		addFileButton.addActionListener(this);
		removeFileButton.addActionListener(this);
		
		fileButtonPanel = new JPanel();
		fileButtonPanel.add(addFileButton);
		fileButtonPanel.add(removeFileButton);
		fileButtonPanel.setOpaque(false);
		
		// Create panel for regex selection
		regexPanel = new JPanel(new BorderLayout());
		regexPanel.setOpaque(false);
		regexPanel.setBorder(BorderFactory.createTitledBorder("Regular Expression File"));
		regexFileField = new JTextField();
		regexFileField.setEditable(false);
		regexButton = new JButton("Load Regex File");
		regexButton.addActionListener(this);
		
		regexPanel.add(regexFileField, BorderLayout.NORTH);
		regexPanel.add(regexButton, BorderLayout.SOUTH);
		
		generateButton = new JButton("Generate Mappings");
		generateButton.addActionListener(this);
		
		generationTimeLabel = new JLabel();
		
		// Create panel for generation stats
		JPanel statsPanel = new JPanel();
		statsPanel.setOpaque(false);
		statsPanel.setBorder(BorderFactory.createTitledBorder("Generation Stats"));
		
		JPanel statsLabelPanel = new JPanel(new GridLayout(0,1));
		statsLabelPanel.setOpaque(false);
		JLabel numMethodsLabel = new JLabel("Unique methods:");
		numMethodsLabel.setOpaque(false);
		JLabel numRegexLabel = new JLabel("Regular Expressions:");
		numRegexLabel.setOpaque(false);
		statsLabelPanel.add(numMethodsLabel);
		statsLabelPanel.add(numRegexLabel);
		
		JPanel statsValuePanel = new JPanel(new GridLayout(0,1));
		statsValuePanel.setOpaque(false);
		numMethodsValueLabel = new JLabel("0");
		numMethodsValueLabel.setOpaque(false);
		numRegexValueLabel = new JLabel("0");
		numRegexValueLabel.setOpaque(false);
		statsValuePanel.add(numMethodsValueLabel);
		statsValuePanel.add(numRegexValueLabel);
		
		statsPanel.add(statsLabelPanel, BorderLayout.CENTER);
		statsPanel.add(statsValuePanel, BorderLayout.LINE_END);
		
		// Set up the checkbox to do an eliminating pass
		runEliminatingPassCheckbox = new JCheckBox("Do initial eliminating pass", false);
		runEliminatingPassCheckbox.addItemListener(this);
		
		fileSelectTabPanel.add(runEliminatingPassCheckbox);
		
		fileSelectTabPanel.add(statsPanel);
		
		fileSelectTabPanel.add(fileButtonPanel);
		
		fileSelectTabPanel.add(fileListScroller);
		
		fileSelectTabPanel.add(regexPanel);
		
		fileSelectTabPanel.add(generateButton);
		
		fileSelectTabPanel.add(generationTimeLabel);

		fileSelectionLayout.putConstraint(SpringLayout.WEST, fileListScroller,
				15, SpringLayout.WEST, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.NORTH, fileListScroller,
				10, SpringLayout.NORTH, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.SOUTH, fileListScroller,
				-15, SpringLayout.NORTH, fileButtonPanel);

		fileSelectionLayout.putConstraint(SpringLayout.SOUTH, fileButtonPanel,
				-15, SpringLayout.SOUTH, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.WEST, fileButtonPanel,
				15, SpringLayout.WEST, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.EAST, fileListScroller,
				-15, SpringLayout.HORIZONTAL_CENTER, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.EAST, fileButtonPanel,
				-15, SpringLayout.HORIZONTAL_CENTER, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.WEST, regexPanel, 15,
				SpringLayout.HORIZONTAL_CENTER, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.EAST, regexPanel, -15,
				SpringLayout.EAST, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.NORTH, regexPanel, 10,
				SpringLayout.NORTH, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.SOUTH, generateButton,
				-15, SpringLayout.SOUTH, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.EAST, generateButton,
				-15, SpringLayout.EAST, fileSelectTabPanel);

		fileSelectionLayout.putConstraint(SpringLayout.SOUTH,
				generationTimeLabel, -15, SpringLayout.NORTH, generateButton);

		fileSelectionLayout.putConstraint(SpringLayout.EAST, generationTimeLabel, -20,
				SpringLayout.EAST, fileSelectTabPanel);
		
		fileSelectionLayout.putConstraint(SpringLayout.WEST, statsPanel, 15,
				SpringLayout.HORIZONTAL_CENTER, fileSelectTabPanel);
		
		fileSelectionLayout.putConstraint(SpringLayout.EAST, statsPanel, -15,
				SpringLayout.EAST, fileSelectTabPanel);
		
		fileSelectionLayout.putConstraint(SpringLayout.NORTH, statsPanel, 40,
				SpringLayout.SOUTH, regexPanel);
		
		fileSelectionLayout.putConstraint(SpringLayout.NORTH, runEliminatingPassCheckbox, 40,
				SpringLayout.SOUTH, statsPanel);
		
		fileSelectionLayout.putConstraint(SpringLayout.WEST, runEliminatingPassCheckbox, 0,
				SpringLayout.WEST, statsPanel);
		
		// Set up tab for displaying regex mappings
		regexList = new JList<>();
		regexList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		regexList.addListSelectionListener(this);
		methodList = new JList<>();
		
		JSplitPane splitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				new JScrollPane(regexList),
				new JScrollPane(methodList));
		
		selectedRegexMethodCountLabel = new JLabel("Count: 0");
		
		JPanel splitPaneContainer = new JPanel(new BorderLayout());
		splitPaneContainer.add(splitpane, BorderLayout.CENTER);
		splitPaneContainer.add(selectedRegexMethodCountLabel, BorderLayout.PAGE_END);
		
		// Set up tabbed pane (one tab for file picking, another for results)
		JTabbedPane tabpane = new JTabbedPane();
		tabpane.addTab("File Selection", null, fileSelectTabPanel);
		tabpane.addTab("RegEx Mapping", splitPaneContainer);
		
		appframe.add(tabpane);
		
		// Behold!
		appframe.setVisible(true);
	}
	
	private static void scanJARFile(File toScan, HashSet<String> methods) {
		try(JarFile jarFile = new JarFile(toScan)) {
			Enumeration<JarEntry> entries = jarFile.entries();
			ArrayList<JarEntry> jarClassFiles = new ArrayList<>();
			
			// Find every .class file in JAR
			while(entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String elementName = entry.getName();
				int extensionStart = elementName.lastIndexOf('.');
				
				if(extensionStart < 0) {
					continue;
				}
				
				String extension = elementName.substring(elementName.lastIndexOf('.'));
				
				if(extension.equals(".class")) {
					jarClassFiles.add(entry);
				}
			}
			
			// Parse each .class file
			for(JarEntry classFile : jarClassFiles) {
				ClassReader reader = new ClassReader(jarFile.getInputStream(classFile));
				reader.accept(new ClassInspector(methods), 0);
			}
		} catch(IOException e) {
			System.out.println("\n\nERROR reading JAR file!");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		if(DEBUG_MODE) {
			System.out.println("main: " + Thread.currentThread().getId());
		}
		
		new RegexScanner();
	}
	
	
	private class JavaFileLoader extends SwingWorker<LinkedHashMap<String, ArrayList<String>>, Void> {
		private File[] javaFiles = null;
		private File regexFileToScan = null;
		private boolean doEliminatingPass = false;
		
		public JavaFileLoader(File[] javaFilesToScan, File regexFileToScan, boolean doEliminatingPass) {
			javaFiles = javaFilesToScan;
			this.regexFileToScan = regexFileToScan;
			this.doEliminatingPass = doEliminatingPass;
		}
		
		@Override
		public LinkedHashMap<String, ArrayList<String>> doInBackground() {
			if(DEBUG_MODE) {
				System.out.println("JavaFileLoader doInBackground: " + Thread.currentThread().getId());
			}
			
			LinkedHashSet<String> methods = new LinkedHashSet<>();
			
			for(File toScan : javaFiles) {
				String extension = toScan.getName().substring(toScan.getName().lastIndexOf('.'));
				
				if(extension.equals(".jar")) {
					scanJARFile(toScan, methods);
				} else {
					try(FileInputStream classFile = new FileInputStream(toScan)) {
						ClassReader reader = new ClassReader(classFile);
						reader.accept(new ClassInspector(methods), 0);
					} catch (Exception e) {
						System.out.format("ERROR: Problem reading file \"%s\"\n", toScan.getName());
						System.out.println(e.getMessage());
						continue;
					}
				}
			}
			
			ArrayList<String> regexes = new ArrayList<>();
			
			if (regexFileToScan == null) {
				return null;
			}

			try (BufferedReader br = new BufferedReader(new FileReader(regexFileToScan))) {
				String regex = null;
				while ((regex = br.readLine()) != null) {
					if(regex.trim().length() > 0) {
						regexes.add(regex);
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			
			// RUN ELIMINATING PASS
			if(doEliminatingPass) {
				if(DEBUG_MODE) {
					System.out.println("Running eliminating pass\n");
				}
				
				StringBuilder megaRegex = new StringBuilder();
				megaRegex.append('(');
				for(String regex : regexes) {
					megaRegex.append('(');
					megaRegex.append(regex);
					megaRegex.append(")|");
				}
				megaRegex.deleteCharAt(megaRegex.length() - 1);
				megaRegex.append(')');

				Pattern megaPat = Pattern.compile(megaRegex.toString());

				LinkedHashSet<String> keepMethods = new LinkedHashSet<>();
				for(String methodCall : methods) {
					Matcher megaMatcher = megaPat.matcher(methodCall);
					if(megaMatcher.find()) {
						keepMethods.add(methodCall);
					}
				}

				methods.retainAll(keepMethods);
			}
			// END ELIMINATING PASS
			
			numMethods = methods.size();
			
			LinkedHashMap<String, ArrayList<String>> mappings = new LinkedHashMap<>(regexes.size());
			
			final int numRegexes = regexes.size();
			final int regexPerThread = (int) Math.ceil((double) numRegexes / (double) NUM_THREADS);
			
			ArrayList<MapGenerator> toRun = new ArrayList<>(NUM_THREADS);
			
			int regexLeft = numRegexes;
			for(int i = 0; i < NUM_THREADS; i++) {
				int numToScan;
				
				if(regexLeft < regexPerThread) {
					numToScan = regexLeft;
				} else {
					numToScan = regexPerThread;
				}
				
				toRun.add(new MapGenerator(methods, regexes, i * regexPerThread, numToScan));
				
				regexLeft -= numToScan;
			}
			

			List<Future<LinkedHashMap<String, ArrayList<String>>>> futures;
			
			try {
				futures = pool.invokeAll(toRun);
			} catch (InterruptedException e) {
				System.out.println("ERROR in MapGenerator thread");
				e.printStackTrace();
				return null;
			}
			
			for(Future<LinkedHashMap<String, ArrayList<String>>> future : futures) {
				try {
					mappings.putAll(future.get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					return null;
				}
			}
			
//			for(String regex : regexes) {
//				Pattern pat = Pattern.compile(regex);
//				ArrayList<String> mappedmethods = new ArrayList<>();
//				
//				for(String methodcall : methods) {
//					Matcher match = pat.matcher(methodcall);
//					if(match.find()) {
//						mappedmethods.add(methodcall);
//					}
//				}
//				
//				mappings.put(regex, mappedmethods);
//			}
			
			return mappings;
		}
		
		@Override
		public void done() {
			if(DEBUG_MODE) {
				System.out.println("JavaFileLoader done: " + Thread.currentThread().getId());
			}
			
			try {
				generatedMappings = get();
			} catch (InterruptedException | ExecutionException e) {
				System.out.println("ERROR: Returning from JavaFileLoader execution\n");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}

			generateComplete();
		}
	}
	
	private class MapGenerator implements Callable<LinkedHashMap<String, ArrayList<String>>> {
		private final int startIndex;
		private final int numMaps;
		private final ArrayList<String> regexList;
		private final LinkedHashSet<String> methodList;
		
		public MapGenerator(LinkedHashSet<String> methodList, ArrayList<String> regexList, int startIndex, int numMaps) {
			this.startIndex = startIndex;
			this.numMaps = numMaps;
			this.regexList = regexList;
			this.methodList = methodList;
		}
		
		@Override
		public LinkedHashMap<String, ArrayList<String>> call() {
			if(DEBUG_MODE) {
				System.out.format("MapGenerator (Start: %d, Num: %d) call: %d\n\n", startIndex, numMaps, Thread.currentThread().getId());
			}
			
			LinkedHashMap<String, ArrayList<String>> maps = new LinkedHashMap<>(numMaps);
			
			for(int i = startIndex; i < startIndex + numMaps; i++) {
				String regex = regexList.get(i);
				Pattern pat = Pattern.compile(regex);
				ArrayList<String> mappedMethods = new ArrayList<>();
				
				for(String methodCall : methodList) {
					Matcher match = pat.matcher(methodCall);
					if(match.find()) {
						mappedMethods.add(methodCall);
					}
				}
				
				maps.put(regex, mappedMethods);
			}
			
			return maps;
		}
	}
}
