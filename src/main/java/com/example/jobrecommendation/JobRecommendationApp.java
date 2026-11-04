package com.example.jobrecommendation;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JobRecommendationApp extends JFrame {
    private static final Color INK = new Color(25, 37, 54);
    private static final Color MUTED = new Color(105, 119, 138);
    private static final Color BRAND = new Color(39, 91, 214);
    private static final Color CANVAS = new Color(245, 247, 251);
    private final ResumeService resumeService = new ResumeService();
    private final AdzunaJobSearch jobSearch = new AdzunaJobSearch();
    private final JTextField queryField = new JTextField("software developer", 18);
    private final JTextField locationField = new JTextField("Bangalore", 12);
    private final JComboBox<String> countryField = new JComboBox<>(new String[]{"India (in)", "United States (us)", "United Kingdom (gb)", "Canada (ca)", "Australia (au)"});
    private final JLabel fileLabel = new JLabel("No resume selected");
    private final JLabel statusLabel = new JLabel("Choose a resume, prepare your matching profile, then search current job listings. Listings are generated sample data for demo purposes.");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Match", "Role", "Company", "Location", "Salary", "Experience"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable jobsTable = new JTable(tableModel);
    private Path selectedFile;
    private Set<String> extractedSkills = Set.of();
    private Integer experienceYears;
    private List<JobMatch> visibleMatches = List.of();

    private JobRecommendationApp() {
        super("Resume Based Job Recommendations");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1240, 780);
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        JButton chooseButton = new JButton("Choose resume");
        JButton extractButton = new JButton("Prepare profile");
        JButton searchButton = new JButton("Find matching jobs");
        styleButton(chooseButton, false);
        styleButton(extractButton, false);
        styleButton(searchButton, true);
        extractButton.setEnabled(false);
        searchButton.setEnabled(false);
        chooseButton.addActionListener(event -> chooseResume(extractButton));
        extractButton.addActionListener(event -> processResume(extractButton, searchButton));
        searchButton.addActionListener(event -> searchJobs(searchButton));

        JPanel resumeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        resumeRow.setOpaque(false);
        resumeRow.add(chooseButton);
        resumeRow.add(fileLabel);
        resumeRow.add(extractButton);
        fileLabel.setForeground(MUTED);
        fileLabel.setToolTipText("Selected resume file");
        JPanel resumeCard = card(new BorderLayout(0, 4));
        resumeCard.add(sectionLabel("1  ·  YOUR RESUME"), BorderLayout.NORTH);
        resumeCard.add(resumeRow, BorderLayout.CENTER);

        JPanel searchRow = new JPanel(new GridBagLayout());
        searchRow.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 12);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.weightx = 1; searchRow.add(fieldGroup("ROLE OR KEYWORDS", queryField), c);
        c.gridx = 1; c.weightx = .65; searchRow.add(fieldGroup("LOCATION", locationField), c);
        c.gridx = 2; c.weightx = .6; searchRow.add(fieldGroup("COUNTRY", countryField), c);
        c.gridx = 3; c.weightx = 0; c.insets = new Insets(4, 4, 4, 0); searchRow.add(searchButton, c);
        JPanel searchCard = card(new BorderLayout(0, 6));
        searchCard.add(sectionLabel("2  ·  FIND YOUR NEXT ROLE"), BorderLayout.NORTH);
        searchCard.add(searchRow, BorderLayout.CENTER);

        JLabel title = new JLabel("Find work that fits your experience");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Search current openings and see how well your resume matches each role.");
        subtitle.setForeground(MUTED);
        JPanel intro = new JPanel();
        intro.setOpaque(false);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        intro.add(title); intro.add(Box.createVerticalStrut(5)); intro.add(subtitle);
        JPanel cards = new JPanel(new GridLayout(2, 1, 0, 12));
        cards.setOpaque(false);
        cards.add(resumeCard); cards.add(searchCard);
        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setBackground(CANVAS);
        top.setBorder(BorderFactory.createEmptyBorder(26, 30, 16, 30));
        top.add(intro, BorderLayout.NORTH); top.add(cards, BorderLayout.CENTER);

        styleField(queryField); styleField(locationField); styleCombo(countryField);

        jobsTable.setFillsViewportHeight(true);
        jobsTable.setAutoCreateRowSorter(true);
        jobsTable.setRowHeight(42);
        jobsTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        jobsTable.setForeground(INK);
        jobsTable.setBackground(Color.WHITE);
        jobsTable.setSelectionBackground(new Color(229, 237, 255));
        jobsTable.setSelectionForeground(INK);
        jobsTable.setShowGrid(false);
        jobsTable.setIntercellSpacing(new Dimension(0, 1));
        jobsTable.setFillsViewportHeight(true);
        jobsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        jobsTable.getTableHeader().setForeground(MUTED);
        jobsTable.getTableHeader().setBackground(new Color(249, 250, 252));
        jobsTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        jobsTable.setRowMargin(1);
        jobsTable.getColumnModel().getColumn(0).setPreferredWidth(78);
        jobsTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        jobsTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        jobsTable.getColumnModel().getColumn(3).setPreferredWidth(190);
        jobsTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        jobsTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        jobsTable.getColumnModel().getColumn(0).setCellRenderer(new MatchRenderer());
        JScrollPane results = new JScrollPane(jobsTable);
        results.setBorder(BorderFactory.createLineBorder(new Color(228, 233, 241)));
        results.getViewport().setBackground(Color.WHITE);
        JLabel resultsTitle = new JLabel("Recommended openings");
        resultsTitle.setFont(new Font("SansSerif", Font.BOLD, 17)); resultsTitle.setForeground(INK);
        JLabel resultsHint = new JLabel("Sorted by match score  ·  Double-click a role to open the application page");
        resultsHint.setForeground(MUTED);
        JPanel resultsHeading = new JPanel(new BorderLayout(0, 5)); resultsHeading.setOpaque(false);
        resultsHeading.add(resultsTitle, BorderLayout.NORTH); resultsHeading.add(resultsHint, BorderLayout.SOUTH);
        JPanel resultsPanel = new JPanel(new BorderLayout(0, 12));
        resultsPanel.setBackground(Color.WHITE);
        resultsPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 18, 30));
        resultsPanel.add(resultsHeading, BorderLayout.NORTH); resultsPanel.add(results, BorderLayout.CENTER);
        statusLabel.setForeground(MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(11, 30, 12, 30));
        statusLabel.setBackground(new Color(249, 250, 252)); statusLabel.setOpaque(true);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        getRootPane().setDefaultButton(searchButton);
        getRootPane().registerKeyboardAction(e -> openSelectedJob(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        jobsTable.setToolTipText("Double-click a job to open its application page");
        jobsTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) openSelectedJob();
            }
        });
        getContentPane().setBackground(CANVAS);
        add(top, BorderLayout.NORTH);
        add(resultsPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(228, 233, 241)),
                BorderFactory.createEmptyBorder(13, 16, 12, 16)));
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(MUTED);
        return label;
    }

    private JPanel fieldGroup(String label, JComponent field) {
        JPanel group = new JPanel(new BorderLayout(0, 5)); group.setOpaque(false);
        group.add(sectionLabel(label), BorderLayout.NORTH); group.add(field, BorderLayout.CENTER);
        return group;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setForeground(INK); field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(213, 220, 231)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        combo.setBackground(Color.WHITE); combo.setForeground(INK);
        combo.setBorder(BorderFactory.createLineBorder(new Color(213, 220, 231)));
        combo.setPreferredSize(new Dimension(155, 38));
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(primary ? Color.WHITE : BRAND);
        button.setBackground(primary ? BRAND : Color.WHITE);
        button.setFocusPainted(false); button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? BRAND : new Color(204, 216, 239)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static final class MatchRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                  boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
            if (!selected) { label.setForeground(new Color(31, 126, 91)); label.setBackground(Color.WHITE); }
            return label;
        }
    }

    private void chooseResume(JButton extractButton) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a PDF, DOC, DOCX, TXT, or image resume (10 MB max)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile().toPath();
            fileLabel.setText(selectedFile.getFileName().toString());
            extractButton.setEnabled(true);
        }
    }

    private void processResume(JButton extractButton, JButton searchButton) {
        extractButton.setEnabled(false);
        statusLabel.setText("Reading resume and extracting skills and experience…");
        new SwingWorker<Profile, Void>() {
            @Override protected Profile doInBackground() throws Exception {
                String text = resumeService.extractText(selectedFile);
                return new Profile(ResumeService.extractSkills(text), ResumeService.extractExperienceYears(text));
            }
            @Override protected void done() {
                try {
                    Profile profile = get();
                    extractedSkills = profile.skills;
                    experienceYears = profile.years;
                    searchButton.setEnabled(true);
                    statusLabel.setText(profileSummary() + " Enter a role and location to search live jobs.");
                } catch (Exception exception) {
                    showError(exception, "Could not read resume");
                } finally {
                    extractButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private String profileSummary() {
        return "Resume prepared for matching (" + extractedSkills.size() + " skill concepts detected; experience "
                + (experienceYears == null ? "not detected" : experienceYears + " years") + ").";
    }

    private void searchJobs(JButton searchButton) {
        String query = queryField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a role or search term, such as Java developer.", "Search term required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String location = locationField.getText().trim();
        String country = ((String) countryField.getSelectedItem()).replaceAll(".*\\((..?)\\)$", "$1");
        searchButton.setEnabled(false);
        statusLabel.setText("Generating sample listings…");
        new SwingWorker<List<Job>, Void>() {
            @Override protected List<Job> doInBackground() throws Exception { return jobSearch.search(query, location, country); }
            @Override protected void done() {
                try {
                    List<Job> jobs = get();
                    tableModel.setRowCount(0);
                    visibleMatches = JobMatcher.rank(jobs, extractedSkills, experienceYears);
                    for (JobMatch match : visibleMatches) {
                        tableModel.addRow(new Object[]{match.matchPercentage() + "%", match.job().title(), match.job().company(),
                                match.job().location(), match.job().salaryRange(), match.experienceNote()});
                    }
                    statusLabel.setText(jobs.size() + " sample listings loaded. Double-click a row to open its application page.");
                } catch (Exception exception) {
                    showError(exception, "Job search failed");
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void openSelectedJob() {
        int row = jobsTable.getSelectedRow();
        if (row < 0) return;
        int modelRow = jobsTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= visibleMatches.size()) return;
        String url = visibleMatches.get(modelRow).job().applyUrl();
        try {
            if (url.isBlank()) throw new IllegalStateException("This listing has no application link.");
            URI listing = URI.create(url);
            if (!"https".equalsIgnoreCase(listing.getScheme()) || listing.getHost() == null) {
                throw new IllegalStateException("The listing did not provide a safe HTTPS link.");
            }
            Desktop.getDesktop().browse(listing);
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not open listing: " + exception.getMessage(), "Open job", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showError(Exception exception, String title) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        statusLabel.setText(title + ": " + message);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private record Profile(Set<String> skills, Integer years) { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JobRecommendationApp().setVisible(true));
    }
}
