package ch.martinelli.demo.keycloak.views;

import ch.martinelli.demo.keycloak.data.entity.Configuration;
import ch.martinelli.demo.keycloak.data.entity.SqlDefinition;
import ch.martinelli.demo.keycloak.data.entity.TableInfo;
import ch.martinelli.demo.keycloak.data.service.ConfigurationService;
import ch.martinelli.demo.keycloak.data.service.SqlDefinitionService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Article;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.BoxSizing;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
//import org.vaadin.tatu.Tree;

import java.io.*;
import java.sql.*;
import java.util.*;

@PageTitle("Table Viewer")
@Route(value = "table-view", layout= MainLayout.class)
@RolesAllowed({"ADMIN","USER"})
public class TableView extends VerticalLayout {

    private String exportPath;
    String myPath;

    private ConfigurationService service;
    private SqlDefinitionService sqlDefinitionService;
    private JdbcTemplate jdbcTemplate;
    private static ComboBox<Configuration> comboBox;
    //private Article descriptionTextField;
    private TextArea sqlTextField;
    private Details queryDetails;
    public static Connection conn;
    private ResultSet resultset;
    private Button exportButton = new Button("Export");
    private Button runButton = new Button("Run");
    private String aktuelle_SQL="";
    private String aktuelle_Tabelle="";
    private Anchor anchor = new Anchor(getStreamResource(aktuelle_Tabelle + ".xls", "default content"), "click to download");

    Grid<Map<String, Object>> grid2 = new Grid<>();

    // PaginatedGrid<String, Object> grid = new PaginatedGrid<>();

    private static String url;
    private static String user;
    private static String password;

    public TableView(@Value("${csv_exportPath}") String p_exportPath, ConfigurationService service, SqlDefinitionService sqlDefinitionService, JdbcTemplate jdbcTemplate) throws SQLException, IOException {
        //add(new H1("Table View"));
        this.exportPath = p_exportPath;
        this.sqlDefinitionService = sqlDefinitionService;
        this.jdbcTemplate = jdbcTemplate;

        System.out.println("Export Path: " + exportPath);
        anchor.getElement().setAttribute("download",true);
        anchor.setEnabled(false);
        exportButton.setVisible(false);
        runButton.setEnabled(false);

        comboBox = new ComboBox<>("Verbindung");

        List<Configuration> configList = service.findMessageConfigurations();
        comboBox.setItems(configList);
        comboBox.setValue(configList.get(1) );

        comboBox.setItemLabelGenerator(Configuration::get_Message_Connection);

        //  comboBox.setValue(service.findAllConfigurations().stream().findFirst().get());

        HorizontalLayout hl = new HorizontalLayout();
        hl.add(comboBox);
        hl.setAlignItems(Alignment.BASELINE);
        setSizeFull();
        // add(hl);

        //Export Button

        exportButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        exportButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        exportButton.addClickListener(clickEvent -> {
            Notification.show("Exportiere " + aktuelle_Tabelle);
            //System.out.println("aktuelle_SQL:" + aktuelle_SQL);
            try {
                generateExcel(exportPath + aktuelle_Tabelle + ".xls",aktuelle_SQL);

                File file= new File(exportPath + aktuelle_Tabelle +".xls");
                StreamResource streamResource = new StreamResource(file.getName(),()->getStream(file));

                anchor.setHref(streamResource);
                //anchor = new Anchor(streamResource, String.format("%s (%d KB)", file.getName(), (int) file.length() / 1024));

                anchor.setEnabled(true);
                exportButton.setVisible(false);
                //      download("c:\\tmp\\" + aktuelle_Tabelle + ".xls");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        runButton.addClickListener(clickEvent -> {
            try {
                show_grid(sqlTextField.getValue());
                anchor.setEnabled(false);
                exportButton.setVisible(true);
                runButton.setEnabled(false);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        HorizontalLayout treehl = new HorizontalLayout();
        treehl.add(createTreeGrid(), createSQLTextField());

      //  treehl.setWidthFull();
        treehl.setAlignItems(Alignment.BASELINE);

        queryDetails = new Details("SQL Auswahl",treehl);
        queryDetails.setOpened(true);
        queryDetails.setWidthFull();
        queryDetails.setSummaryText("Bitte Abfrage auswählen");


        add(hl, queryDetails);

        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.add(runButton, exportButton, anchor);
        horizontalLayout.setAlignItems(Alignment.BASELINE);
        add(horizontalLayout, grid2);
    }

    /*
    private Article createDescriptionTextField() {
        descriptionTextField = new Article();
      //  descriptionTextField.setReadOnly(true); // Set as read-only as per your requirement
        descriptionTextField.setWidthFull();
        descriptionTextField.setText("Bitte Abfrage auswählen.");
        return descriptionTextField;
    }

     */

    private TextArea createSQLTextField() {
        sqlTextField = new TextArea("Query");
        sqlTextField.setReadOnly(true); // Set as read-only as per your requirement
        //sqlTextField.setMaxLength(2000);
        //sqlTextField.setWidth("600px");
        sqlTextField.setClassName("tfwb");
        return sqlTextField;
    }
    private TreeGrid createTreeGrid() {
        TreeGrid<SqlDefinition> treeGrid = new TreeGrid<>();
        treeGrid.setItems(sqlDefinitionService.getRootProjects(), sqlDefinitionService ::getChildProjects);
        treeGrid.addHierarchyColumn(SqlDefinition::getName);
        treeGrid.getColumns().forEach(col -> col.setAutoWidth(true));
     //   treeGrid.setWidth("200px");
        treeGrid.addExpandListener(event->
                System.out.println(String.format("Expanded %s item(s)",event.getItems().size()))
        );
        treeGrid.addCollapseListener(event->
                System.out.println(String.format("Collapsed %s item(s)",event.getItems().size()))
        );
        treeGrid.asSingleSelect().addValueChangeListener(event->{

            SqlDefinition selectedItem=event.getValue();
            if(selectedItem != null){
                String sql = selectedItem.getSql();
                if(sql == null) {
                    sql = "";
                }

                queryDetails.setSummaryText(selectedItem.getBeschreibung());

                sqlTextField.setValue(sql);
                System.out.println("jetzt Ausführen: " + selectedItem.getSql());
                aktuelle_SQL = sql;
                aktuelle_Tabelle = selectedItem.getName();
                runButton.setEnabled(true);
            }
        });
        return treeGrid;
    }

  /*  private Tree createTree(){
        Tree<SqlDefinition> tree = new Tree<>(
                SqlDefinition::getName);
        System.out.println(sqlDefinitionService.getRootProjects().size()+"..............vvvvvvvvvvvvvvvvvvvvvvvvvv");

        tree.setAllRowsVisible(true);
        tree.setItems(sqlDefinitionService.getRootProjects(),
                sqlDefinitionService::getChildProjects);
    //    tree.setItemIconProvider(item -> getIcon(item));
     //   tree.setItemIconSrcProvider(item -> getImageIconSrc(item));
      //  tree.setItemTitleProvider(SqlDefinition::getManager);

        tree.addExpandListener(event->
                System.out.println(String.format("Expanded%sitem(s)",event.getItems().size()))
        );
        tree.addCollapseListener(event->
                System.out.println(String.format("Collapsed%sitem(s)",event.getItems().size()))
        );
        tree.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null)
                System.out.println(event.getValue().getName() + " selected");
        });
        tree.setHeight("350px"); //tree.addClassNames("text-l","m-m");
        tree.addClassNames(LumoUtility.FontSize.XXSMALL, LumoUtility.Margin.NONE);



        // add(tree);
        return tree;
    }
    private void createTreeOld(){

        Tree<SqlDefinition> tree=new Tree<>(SqlDefinition::getName);
        System.out.println(sqlDefinitionService.getAllSqlDefinitions()+"#######################");
        tree.setItems(sqlDefinitionService.getRootProjects(),sqlDefinitionService::getChildProjects);

        tree.addExpandListener(event->
                System.out.println(String.format("Expanded%sitem(s)",event.getItems().size()))
        );
        tree.addCollapseListener(event->
                System.out.println(String.format("Collapsed%sitem(s)",event.getItems().size()))
        );
        tree.asSingleSelect().addValueChangeListener(event->{

            SqlDefinition selectedItem=event.getValue();
            if(selectedItem!=null){
                System.out.println("where..........");
            }
        });
        tree.setAllRowsVisible(true);

        tree.addClassNames(LumoUtility.FontSize.XXSMALL,LumoUtility.Margin.NONE);

        add(tree);
    }
*/

    private InputStream getStream(File file) {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return stream;
    }

    public StreamResource getStreamResource(String filename, String content) {
        return new StreamResource(filename,
                () -> new ByteArrayInputStream(content.getBytes()));
    }

    public static void fileOutputStreamByteSingle(String file, String data) throws IOException {
        byte[] bytes = data.getBytes();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }


    private void show_grid(String sql) throws SQLException, IOException {
        System.out.println("Execute SQL: " + sql );
        // Create the grid and set its items
        //Grid<LinkedHashMap<String, Object>> grid2 = new Grid<>();
        grid2.removeAllColumns();

        //List<LinkedHashMap<String,Object>> rows = retrieveRows("select * from EKP.ELA_FAVORITEN where rownum<200");
        List<Map<String,Object>> rows = retrieveRows(sql);

        if(!rows.isEmpty()){
            grid2.setItems( rows); // rows is the result of retrieveRows

            // Add the columns based on the first row
            Map<String, Object> s = rows.get(0);
            for (Map.Entry<String, Object> entry : s.entrySet()) {
                grid2.addColumn(h -> h.get(entry.getKey().toString())).setHeader(entry.getKey()).setAutoWidth(true).setResizable(true).setSortable(true);
            }

            grid2.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
            grid2.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
            grid2.addThemeVariants(GridVariant.LUMO_COMPACT);
            //   grid2.setAllRowsVisible(true);
            grid2.setPageSize(50);
            grid2.setHeight("800px");

            grid2.getStyle().set("resize", "vertical");
            grid2.getStyle().set("overflow", "auto");

            //grid2.setPaginatorSize(5);
            // Add the grid to the page

//            this.setPadding(false);
//            this.setSpacing(false);
//            this.setBoxSizing(BoxSizing.CONTENT_BOX);

        }
        else {
            //Text txt = new Text("Es konnten keine Daten  abgerufen werden!");
            //add(txt);
        }

    }

    public List<Map<String,Object>> retrieveRows(String queryString) {
        if (queryString != null) {
            try {
                return jdbcTemplate.queryForList(queryString);
            } catch (Exception e) {
                e.printStackTrace();
                Notification.show(e.getMessage(), 3000, Notification.Position.TOP_CENTER);
            }
        }
        return Collections.emptyList();
    }

    public List<LinkedHashMap<String,Object>> retrieveRows_old(String queryString) throws SQLException, IOException {

        List<LinkedHashMap<String, Object>> rows = new LinkedList<LinkedHashMap<String, Object>>();

        if(queryString != null) {

            PreparedStatement s = null;
            ResultSet rs = null;
            try {
                //    String url="jdbc:oracle:thin:@37.120.189.200:1521:xe";
                //    String user="SYSTEM";
                //    String password="Michael123";

                DriverManagerDataSource ds = new DriverManagerDataSource();
                Configuration conf;
                conf = comboBox.getValue();

                Class.forName("oracle.jdbc.driver.OracleDriver");

                //    Connection conn=DriverManager.getConnection(url, user, password);
                Connection conn = DriverManager.getConnection(conf.getDb_Url(), conf.getUserName(), conf.getPassword());


                s = conn.prepareStatement(queryString);

                int timeout = s.getQueryTimeout();
                if (timeout != 0)
                    s.setQueryTimeout(0);

                rs = s.executeQuery();


                List<String> columns = new LinkedList<>();
                ResultSetMetaData resultSetMetaData = rs.getMetaData();
                int colCount = resultSetMetaData.getColumnCount();
                for (int i = 1; i < colCount + 1; i++) {
                    columns.add(resultSetMetaData.getColumnLabel(i));
                }

                while (rs.next()) {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
                    for (String col : columns) {
                        int colIndex = columns.indexOf(col) + 1;
                        String object = rs.getObject(colIndex) == null ? "" : String.valueOf(rs.getObject(colIndex));
                        row.put(col, object);
                    }

                    rows.add(row);
                }
            } catch (SQLException | IllegalArgumentException | SecurityException e) {
                // e.printStackTrace();
                // add(new Text(e.getMessage()));

                Notification notification = Notification.show(e.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);

                return Collections.emptyList();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {

                try {
                    rs.close();
                } catch (Exception e) { /* Ignored */ }
                try {
                    s.close();
                } catch (Exception e) { /* Ignored */ }
                try {
                    conn.close();
                } catch (Exception e) { /* Ignored */ }


            }
        }
        return rows;
    }

    private static void generateExcel(String file, String query) throws IOException {
        Configuration conf;
        conf = comboBox.getValue();

        try {
            //String url="jdbc:oracle:thin:@37.120.189.200:1521:xe";
            //String user="SYSTEM";
            //String password="Michael123";




            Class.forName("oracle.jdbc.driver.OracleDriver");

            //    Connection conn=DriverManager.getConnection(url, user, password);
            Connection conn=DriverManager.getConnection(conf.getDb_Url(), conf.getUserName(), conf.getPassword());

            //   DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());


            PreparedStatement stmt=null;
            //Workbook
            HSSFWorkbook workBook=new HSSFWorkbook();
            HSSFSheet sheet1=null;

            //Cell
            Cell c=null;

            CellStyle cs=workBook.createCellStyle();
            HSSFFont f =workBook.createFont();
            f.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
            f.setFontHeightInPoints((short) 12);
            cs.setFont(f);


            sheet1=workBook.createSheet("Sheet1");


            // String query="select  EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, COMM, DEPTNO, WORK_CITY, WORK_COUNTRY from APEX_040000.WWV_DEMO_EMP";
            stmt=conn.prepareStatement(query);
            ResultSet rs=stmt.executeQuery();

            ResultSetMetaData metaData=rs.getMetaData();
            int colCount=metaData.getColumnCount();

            LinkedHashMap<Integer, TableInfo> hashMap=new LinkedHashMap<Integer, TableInfo>();


            for(int i=0;i<colCount;i++){
                TableInfo tableInfo=new TableInfo();
                tableInfo.setFieldName(metaData.getColumnName(i+1).trim());
                tableInfo.setFieldText(metaData.getColumnLabel(i+1));
                tableInfo.setFieldSize(metaData.getPrecision(i+1));
                tableInfo.setFieldDecimal(metaData.getScale(i+1));
                tableInfo.setFieldType(metaData.getColumnType(i+1));
                //     tableInfo.setCellStyle(getCellAttributes(workBook, c, tableInfo));

                hashMap.put(i, tableInfo);
            }

            //Row and Column Indexes
            int idx=0;
            int idy=0;

            HSSFRow row=sheet1.createRow(idx);
            TableInfo tableInfo=new TableInfo();

            Iterator<Integer> iterator=hashMap.keySet().iterator();

            while(iterator.hasNext()){
                Integer key=(Integer)iterator.next();

                tableInfo=hashMap.get(key);
                c=row.createCell(idy);
                c.setCellValue(tableInfo.getFieldText());
                c.setCellStyle(cs);
                if(tableInfo.getFieldSize() > tableInfo.getFieldText().trim().length()){
                    sheet1.setColumnWidth(idy, (tableInfo.getFieldSize()* 10));
                }
                else {
                    sheet1.setColumnWidth(idy, (tableInfo.getFieldText().trim().length() * 5));
                }
                idy++;
            }

            while (rs.next()) {

                idx++;
                row = sheet1.createRow(idx);
                //  System.out.println(idx);
                for (int i = 0; i < colCount; i++) {

                    c = row.createCell(i);
                    tableInfo = hashMap.get(i);

                    switch (tableInfo.getFieldType()) {
                        case 1:
                            c.setCellValue(rs.getString(i+1));
                            break;
                        case 2:
                            c.setCellValue(rs.getDouble(i+1));
                            break;
                        case 3:
                            c.setCellValue(rs.getDouble(i+1));
                            break;
                        default:
                            c.setCellValue(rs.getString(i+1));
                            break;
                    }
                    c.setCellStyle(tableInfo.getCellStyle());
                }

            }
            rs.close();
            stmt.close();
            conn.close();

            // String path="c:\\tmp\\test.xls";

            FileOutputStream fileOut = new FileOutputStream(file);

            workBook.write(fileOut);
            fileOut.close();


        } catch (SQLException | FileNotFoundException e) {
            System.out.println("Error in Method generateExcel(String file, String query) file: " + file + " query: "  + query);
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}