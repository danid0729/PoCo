import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;        

public class CombinatorBuilder {
	private JPanel polPanel;
	private JPanel panel;
	private JFrame frame;
	private List<JComboBox> selections;
	private int pols;
	private JTextField resultField;
	private void createAndShowGUI() {
        //Create and set up the window.
        frame = new JFrame("Combinator Builder");
        frame.setTitle("Combinator Builder");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel("# of Policies");
        
        final JTextField numPols = new JTextField();
        numPols.setPreferredSize(new Dimension(80, 30));
        
        JButton gen = new JButton("Generate Combinator");
        resultField = new JTextField();
        gen.addActionListener(new java.awt.event.ActionListener() {
        	 public void actionPerformed(java.awt.event.ActionEvent e) {
        		 calculateResult();
        	 }
        });
        
        polPanel = new JPanel();
        polPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        numPols.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
            	try {
            		pols = Integer.parseInt(numPols.getText());
            		if (pols<=0){
            			JOptionPane.showMessageDialog(null,
            					"Error: Please enter number bigger than 0", "Error Message",
                                JOptionPane.ERROR_MESSAGE);
                      	return;
            		}
            	}
            	catch(NumberFormatException ex)
            	{
            		JOptionPane.showMessageDialog(null,
                            "Error: Please enter number", "Error Message",
                            JOptionPane.ERROR_MESSAGE);
            		return;
            	}
            	createGrid();
            }
       });
        
        JPanel headPanel = new JPanel();
        headPanel.add(label);
        headPanel.add(numPols);
        headPanel.add(gen);
        panel.add(headPanel, BorderLayout.PAGE_START);
        panel.add(polPanel, BorderLayout.CENTER);
        JPanel footPanel = new JPanel();
        footPanel.setLayout(new GridLayout(0,1));
        footPanel.add(gen);
        footPanel.add(resultField);
        panel.add(footPanel, BorderLayout.PAGE_END);

        
        JScrollPane scrPane = new JScrollPane(panel);
        scrPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        frame.add(scrPane, BorderLayout.CENTER);        
        frame.setExtendedState(Frame.MAXIMIZED_BOTH); 
        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }
	
	//Constructs the policy grid
	private void createGrid()
	{
    	polPanel.removeAll();
    	polPanel.setLayout(new GridLayout(0,pols + 1));
    	for(int i = 1; i <= pols; i++)
    	{
    		JLabel label = new JLabel("<HTML><U>p" + i + "<U><HTML>");
            polPanel.add(label);
    	}
        polPanel.add(new JLabel("<HTML><U>Result<U><HTML>"));
    	String[] options = { "select", "-", "0", "+" };
    
    	selections = new ArrayList<JComboBox>();
    	for(int i = 1; i <= Math.pow(3,pols); i++)
    	{
    		for(int j = 1; j <= pols; j++)
    		{
    			int value = ((i-1)/(int)Math.pow(3,pols-j))%3 + 1;
    			JLabel label = new JLabel(options[value]);
                polPanel.add(label);
    		}
    		JComboBox<?> list = new JComboBox<Object>(options);
    		selections.add(list);
    		polPanel.add(list);
    	}
    	frame.pack();
    	frame.setExtendedState(Frame.MAXIMIZED_BOTH); 
        frame.setVisible(true);
	}
	
	//Construct the final SRE formula
	private void calculateResult()
	{
		for(JComboBox c : selections)
		 {
			 if(c.getSelectedItem().toString() == "select")
			 {
				 JOptionPane.showMessageDialog(null,
       				 "Please select a result value for all rows.", "RError Message",
                        JOptionPane.ERROR_MESSAGE);
				 return;
			 }
		 }
		 String[] options = { "select", "-", "0", "+" };
		 String result = "Disjunction(";
		 for(int i=1; i<=selections.size(); i++)
		 {
			 result += "Conjunction(";
			 for(int j=1; j<=pols; j++)
			 {
				 int value = ((i-1)/(int)Math.pow(3,pols-j))%3 + 1;
				 switch(options[value])
				 {
				 	case "-":
				 		result += "($p"+j+" = -`%`) ";
				 		break;
				 	case "0":
				 		result += "($p"+j+" = NIL) ";
				 		break;
				 	case "+":
				 		result += "($p"+j+" = +`%`) ";
				 		break;
				 }
				 if(j<pols)
					 result += ", Conjunction(";
				 else if(j==pols)
					 result += ", ";
				 else
					 result += ") ";
			 }
			 
			 switch(selections.get(i-1).getSelectedItem().toString())
			 {
			 	case "-":
			 		result += "-`%`)";
			 		break;
			 	case "0":
			 		result += "NIL)";
			 		break;
			 	case "+":
			 		result += "+`%`)";
			 		break;
			 }
			 if(i<selections.size()-1)
				 result += ", Disjunction(";
			 else if(i==selections.size()-1)
				 result += ", ";
			 else
				 result += ") ";
		}
		resultField.setText(result);
	}
	
	
	public static void main(String[] args) {
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new CombinatorBuilder().createAndShowGUI();
            }
        });
    }
}
