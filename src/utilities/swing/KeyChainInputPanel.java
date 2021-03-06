package utilities.swing;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import core.config.Config;
import core.keyChain.KeyChain;
import core.keyChain.MouseGesture;
import core.keyChain.TaskActivation;

@SuppressWarnings("serial")
public class KeyChainInputPanel extends JPanel {

	private static final int MAX_KEY_CHAIN = 15;

	private static final ReentrantLock inUse = new ReentrantLock();

	private KeyChain keyChain;
	private final DefaultListModel<KeyChain> model;
	private final JList<MouseGesture> mouseGestureList;

	public static void main(String[] args) {
		TaskActivation ac = TaskActivation.newBuilder().build();
		HashSet<MouseGesture> gs = new HashSet<MouseGesture>();
		gs.add(MouseGesture.ALPHA);
		gs.add(MouseGesture.HORIZONTAL);
		ac.setMouseGestures(gs);

		TaskActivation x = getInputKeyChains(null, 1, ac);
		if (x != null) {
			for (MouseGesture g : x.getMouseGestures()) {
				System.out.println(g);
			}
		}
	}

	public static KeyChain getInputKeyChain(JFrame parent, KeyChain prepopulated) {
		Set<KeyChain> prepopulatedSet = new HashSet<>();
		prepopulatedSet.add(prepopulated);
		return getInputKeyChain(parent, prepopulatedSet);
	}

	public static KeyChain getInputKeyChain(JFrame parent, Set<KeyChain> prepopulated) {
		TaskActivation task = getInputKeyChains(
								parent, 1,
								TaskActivation.newBuilder().withHotKeys(prepopulated).build(),
								Mode.KEYCHAIN_ONLY);
		if (task == null) {
			return null;
		}

		Set<KeyChain> keys = task.getHotkeys();

		if (keys != null && keys.size() == 1) {
			return keys.iterator().next();
		}
		return null;
	}

	public static TaskActivation getInputActivation(JFrame parent, TaskActivation prepopulated) {
		inUse.lock();
		try {
			return getInputKeyChains(parent, MAX_KEY_CHAIN, prepopulated);
		} finally {
			inUse.unlock();
		}
	}

	private static TaskActivation getInputKeyChains(JFrame parent, int limit, TaskActivation prepopulated) {
		return getInputKeyChains(parent, limit, prepopulated, Mode.ALL_ACTIVATION);
	}

	/**
	 * Show a panel to prompt user to select an input task activation.
	 *
	 * @param parent parent frame, or null if there is none.
	 * @param limit maximum number of activation entities allowed.
	 * @param prepopulated populate the panel with a set of activation (e.g. existing activation).
	 * @return a new {@link TaskActivation} object representing the user selection.
	 */
	private static TaskActivation getInputKeyChains(JFrame parent, int limit, TaskActivation prepopulated, Mode mode) {
		KeyChainInputPanel input = new KeyChainInputPanel(prepopulated, limit, mode);
		final JOptionPane optionPane = new JOptionPane(input, JOptionPane.INFORMATION_MESSAGE,
				JOptionPane.OK_CANCEL_OPTION);

		final JDialog dialog = new JDialog (parent, "Activation input", true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(optionPane);

		optionPane.addPropertyChangeListener(new PropertyChangeListener() {
	        @Override
			public void propertyChange(PropertyChangeEvent e) {
	            String prop = e.getPropertyName();
	            if (dialog.isVisible() && (e.getSource() == optionPane) &&
	            	(prop.equals(JOptionPane.VALUE_PROPERTY))) {
	                dialog.setVisible(false);
	            }
	        }
	    });


		dialog.pack();
		dialog.setVisible(true);

		try {
			int value = ((Integer)optionPane.getValue()).intValue();
			if (value == JOptionPane.YES_OPTION) {
				Set<KeyChain> keyChains = new HashSet<KeyChain>();
				Enumeration<KeyChain> allKeys = input.model.elements();
				while (allKeys.hasMoreElements()) {
					KeyChain next = allKeys.nextElement();
					if (!next.getKeys().isEmpty()) {
						keyChains.add(next);
					}
				}

				if (!input.keyChain.getKeys().isEmpty()) {
					keyChains.add(input.keyChain);
				}

				Set<MouseGesture> gestures = new HashSet<>();
				for (MouseGesture gesture : input.mouseGestureList.getSelectedValuesList()) {
					gestures.add(gesture);
				}

				return TaskActivation.newBuilder().withHotKeys(keyChains).withMouseGestures(gestures).build();
			}

			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private KeyChainInputPanel(TaskActivation prepopulated, final int limit, Mode mode) {
		keyChain = new KeyChain();

		final JLabel instruction = new JLabel("Start pressing key chain.");
		final JTextField tf = new JTextField();
		tf.setEditable(false);

		tf.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				int code = e.getKeyCode();

				if (code == Config.HALT_TASK) {
					keyChain.getKeys().clear();
				}

				tf.setText(keyChain.toString());
			}

			@Override
			public void keyPressed(KeyEvent e) {
				int code = e.getKeyCode();
				keyChain.getKeys().add(code);
			}
		});

		model = new DefaultListModel<>();
		for (KeyChain key : prepopulated.getHotkeys()) {
			model.addElement(key);
		}

		final JList<KeyChain> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		list.setLayoutOrientation(JList.VERTICAL);
		list.setVisibleRowCount(-1);

		final JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.setPreferredSize(new Dimension(150, 80));

		MouseGesture[] gestures = MouseGesture.enabledGestures().toArray(new MouseGesture[0]);
		mouseGestureList = new JList<>(gestures);
		mouseGestureList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		mouseGestureList.setLayoutOrientation(JList.VERTICAL);
		mouseGestureList.setVisibleRowCount(-1);

		for (int i = 0; i < gestures.length; i++) {
			MouseGesture gesture = gestures[i];
			if (prepopulated.getMouseGestures().contains(gesture)) {
				mouseGestureList.addSelectionInterval(i, i);
			}
		}


		final JScrollPane scrollPaneMouseGesture = new JScrollPane(mouseGestureList);
		scrollPaneMouseGesture.setPreferredSize(new Dimension(150, 160));

		final JButton bAdd = new JButton("Add");
		bAdd.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!keyChain.getKeys().isEmpty()) {
					if (model.getSize() >= limit) {
						return;
					}

					model.addElement(keyChain);
					keyChain = new KeyChain();
					tf.setText("");
				}
			}
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					int selected = list.getSelectedIndex();
					if (selected >= 0) {
						model.remove(list.getSelectedIndex());
					}
				}
			}
		});

		final JPanel addButtonPanel = new JPanel();
		addButtonPanel.setLayout(new BoxLayout(addButtonPanel, BoxLayout.X_AXIS));
		addButtonPanel.add(bAdd);
		addButtonPanel.add(javax.swing.Box.createHorizontalStrut(10));
		addButtonPanel.add(new JLabel("(Select and right click to remove.)"));

		final JPanel basicPanel = new JPanel();
		basicPanel.setLayout(new BoxLayout(basicPanel, BoxLayout.Y_AXIS));
		basicPanel.add(instruction);
		basicPanel.add(tf);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		add(basicPanel);
		if (mode == Mode.ALL_ACTIVATION || mode == Mode.KEYCHAIN_ONLY) {
			add(javax.swing.Box.createVerticalStrut(5));
			add(addButtonPanel);
			add(javax.swing.Box.createVerticalStrut(5));
			add(scrollPane);
		}
		if (mode == Mode.ALL_ACTIVATION || mode == Mode.MOUSE_GESTURE_ONLY) {
			add(javax.swing.Box.createVerticalStrut(5));
			add(scrollPaneMouseGesture);
		}
	}

	/**
	 * @return whether a panel is being displayed.
	 */
	public static boolean isInUse() {
		return inUse.isLocked();
	}

	private static enum Mode {
		ALL_ACTIVATION,
		KEYCHAIN_ONLY,
		MOUSE_GESTURE_ONLY;
	}
}
