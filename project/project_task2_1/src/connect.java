import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class connect {
  //连接数据库所需要的参数
  private static String host;
  private static String dbName;
  private static String user;
  private static String password;

  //json文件单次导入的条数
  private static final int  BATCH_SIZE = 1000;
  //csv文件单次导入的条数
  private static final int  BATCH_SIZE_2 = 50000;

  //执行sql语句所需的组件
  private static Connection conn = null;
  private static PreparedStatement stmt = null;
  private static PreparedStatement stmt2 = null;

  //文件系统的输入来源
  private static JsonArray jsonArray = null;
  private static JsonArray jsonArray_classList = null;
  private static BufferedReader br = null;

  //加速导入用到的数据结构
  private static final Map<String, Integer> MAP_course = new HashMap<>();       //courseId->Id
  private static final Map<String, Integer> MAP_class = new HashMap<>();        //CourseId+className->Id
  private static final Map<String, Integer> MAP_course_name = new HashMap<>();  //courseName->Id

  /***************************************开关数据库相关组件***********************************************/
  //测试数据库开关功能，并且加载数据
  public static void testConnection(String _host, String _dbName, String _user, String _password) {
    System.out.println("\033[33;4m" + "**************  START TEST CONNECTION  **************" + "\033[0m");
    host = _host;
    dbName = _dbName;
    user = _user;
    password = _password;
    OpenDB();
    CloseDB();
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }
  //开启数据库
  private static void OpenDB() {
    String url = "jdbc:postgresql://" + host + "/" + dbName;

    // 1. 加载Driver类，Driver类对象将自动被注册到DriverManager类中
    try {
      Class.forName("org.postgresql.Driver");
    } catch(Exception e) {
      System.err.println("Cannot find the Postgres driver. Check CLASSPATH.");
      System.exit(1);
    }

    // 2. 连接数据库，返回连接对象
    try {
      conn = DriverManager.getConnection(url, user, password);
      System.out.println("Successfully connected to the database " + dbName + " as " + user);
      conn.setAutoCommit(false);
    } catch (SQLException e) {
      System.err.println("Database connection failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
  //依据传入的sql语句建立prepareStatement
  private static void create_PS(String sql) {
    try {
      if(conn != null) {
        stmt = conn.prepareStatement(sql);
      } else {
        System.err.println("Connection unaccomplished");
      }
    } catch (SQLException e) {
      System.err.println("Insert statement failed");
      System.err.println(e.getMessage());
      CloseDB();
      System.exit(1);
    }
  }
  private static void create_PS_2(String sql) {
    try {
      if(conn != null) {
        stmt2 = conn.prepareStatement(sql);
      } else {
        System.err.println("Connection unaccomplished");
      }
    } catch (SQLException e) {
      System.err.println("Insert statement failed");
      System.err.println(e.getMessage());
      CloseDB();
      System.exit(1);
    }
  }
  //关闭数据库
  private static void CloseDB() {
    if (conn != null) {
      try {
        if (stmt != null) {
          stmt.close();
          stmt = null;
        }
        conn.commit();
        conn.close();
        conn = null;
        System.out.println("Successfully close the database " + dbName + " as " + user);
      } catch (Exception e) {
        System.err.println("Close database failed");
        System.err.println(e.getMessage());
        System.exit(1);
      }
    }
  }

  /***************************************加载文件相关组件***********************************************/
  //加载json文件中的信息到jsonArray
  public static void loadFromJson(String fileLocation) {
    try {
      //新建解析json文件的模块
      JsonParser parser = new JsonParser();  //创建JSON解析器
      BufferedReader in = new BufferedReader(
              new InputStreamReader(new FileInputStream(fileLocation), StandardCharsets.UTF_8),
              50 * 1024 * 1024); //设置缓冲区 编码
      jsonArray = (JsonArray) parser.parse(in);  //创建JsonArray对象
    } catch (FileNotFoundException e) {
      System.err.println("No JSON file found");
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
  //加载CSV文件中的信息到BufferedReader
  public static void loadFromCSV(String fileLocation){
    try {
      //新建解析csv的文件模块
      /*br = new BufferedReader(
              new InputStreamReader(
                      new FileInputStream(fileLocation)
              )
      );*/
      br = new BufferedReader(new FileReader(fileLocation));
    } catch (FileNotFoundException e) {
      System.err.println("No CSV file found");
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  /***************************************输入数据库相关组件*********************************************/
  //course数据的输入封装
  public static void insert_Course() {
    System.out.println("\033[33;4m" + "**************  START LOADING COURSE  **************" + "\033[0m");
    //清理原始数据,恢复自增列
    try {
      System.out.println("--START CLEAR TABLE COURSE--");
      OpenDB();
      if (conn != null) {
        Statement stmt0 = conn.createStatement();
        stmt0.execute("TRUNCATE TABLE course RESTART IDENTITY CASCADE");
        stmt0.close();
      }
      CloseDB();
      System.out.println("--FINISH CLEAR TABLE COURSE--\n");
    } catch (SQLException e) {
      System.err.println("TEST Database connection failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }

    int n = jsonArray.size();
    int cnt = 0;
    MAP_course.clear();

    //记录开始时间
    long start = System.currentTimeMillis();
    try {
      System.out.println("--START INSERT INTO TABLE COURSE--");
      OpenDB();

      //准备导入数据库所需的jdbc::PreparedStatement, 以及对应的sql语句
      String sql_addCourseIfNotExist =
              "INSERT INTO course(id, totalCapacity, courseId, courseHour, courseCredit, courseName, courseDept) VALUES(?,?,?,?,?,?,?)"
                      + "ON conflict(courseId)  DO NOTHING;";
      create_PS(sql_addCourseIfNotExist);

      for (int i = 0; i < n; i++) {
        //从json文件中读取对应序号的信息，创造course_info对象，准备后续输入数据库中
        course_info course_info = new course_info();
        //将json文件中的course相关信息，导入course_info对象中
        jsonToCourse(i, course_info, cnt);
        //将course_info对象中的信息导入数据库中
        courseToDatabase(course_info);
        //将对应course_Id->id的关系加载到hashmap中
        if(!MAP_course.containsKey(course_info.getCourseId()))
          MAP_course.put(course_info.getCourseId(), cnt);
        //将对应course_name->id的关系加载到hashmap中
        if(!MAP_course_name.containsKey(course_info.getCourseName()))
          MAP_course_name.put(course_info.getCourseName(), cnt);
        //更新cnt计数器
        cnt++;
        //每加载到一定数值就执行一次数据库导入操作
        if (cnt % BATCH_SIZE == 0) {
          stmt.executeBatch();
          stmt.clearBatch();
          conn.commit();
        }
      }
      //最后再执行一次数据库导入操作
      if (cnt % BATCH_SIZE != 0) {
        stmt.executeBatch();
        stmt.clearBatch();
        conn.commit();
      }
      CloseDB();
      System.out.println("--FINISH INSERT INTO TABLE COURSE--\n");
      System.out.println(cnt + " records successfully loaded in table : COURSE");
    } catch (SQLException se) {
      System.err.println("SQL error: " + se.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    }
    //记录结束时间
    long end = System.currentTimeMillis();
    System.out.println("Loading speed : " + (cnt * 1000)/(end - start) + " records/s");
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }
  //class数据的输入封装
  public static void insert_Class() {
    System.out.println("\033[33;4m" + "**************  START LOADING CLASS  **************" + "\033[0m");
    //清理原始数据,恢复自增列
    try {
      System.out.println("--START CLEAR TABLE CLASS--");
      OpenDB();
      if (conn != null) {
        Statement stmt0 = conn.createStatement();
        stmt0.execute("TRUNCATE TABLE class RESTART IDENTITY CASCADE");
        stmt0.close();
      }
      CloseDB();
      System.out.println("--FINISH CLEAR TABLE CLASS--\n");
    } catch (SQLException e) {
      System.err.println("TEST Database connection failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }

    int n = jsonArray.size();
    int cnt = 0;
    MAP_class.clear();

    //记录开始时间
    long start = System.currentTimeMillis();
    try {
      System.out.println("--START INSERT INTO TABLE CLASS--");
      OpenDB();
      //准备导入数据库所需的jdbc::PreparedStatement, 以及对应的sql语句
      String sql_add_Class = "INSERT INTO class(id, courseId, className) VALUES(?, ?, ?)";
      create_PS(sql_add_Class);

      for (int i = 0; i < n; i++) {
        //从json文件中读取对应序号的信息，创造class_info对象，准备后续输入数据库中
        class_info class_info = new class_info();
        //将json文件中的class相关信息，导入class_info对象中
        jsonToClass(i, class_info, cnt);
        //将class_info对象中的信息导入数据库中
        classToDatabase(class_info);
        //将对应class_name+course_name->id的关系加载到hashmap中
        String key_Id = class_info.getCourseId_real() + class_info.getClassName();
        if(!MAP_class.containsKey(key_Id))
          MAP_class.put(key_Id, cnt);
        //更新cnt计数器
        cnt++;
        if (cnt % BATCH_SIZE == 0) {
          stmt.executeBatch();
          stmt.clearBatch();
          conn.commit();
        }
      }
      if (cnt % BATCH_SIZE != 0) {
        stmt.executeBatch();
        stmt.clearBatch();
        conn.commit();
      }
      CloseDB();
      System.out.println("--FINISH INSERT INTO TABLE CLASS--\n");
      System.out.println(cnt + " records successfully loaded in table : CLASS");
    } catch (SQLException se) {
      System.err.println("SQL error: " + se.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    }
    //记录结束时间
    long end = System.currentTimeMillis();
    System.out.println("Loading speed : " + (cnt * 1000)/(end - start) + " records/s");
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }
  //teacher数据的输入封装
  public static void insert_teacher(){
    System.out.println("\033[33;4m" + "**************  START LOADING TEACHER  **************" + "\033[0m");
    //清理原始数据,恢复自增列
    try {
      System.out.println("--START CLEAR TABLE TEACHER--");
      OpenDB();
      if (conn != null) {
        Statement stmt0 = conn.createStatement();
        stmt0.execute("TRUNCATE TABLE teacher RESTART IDENTITY CASCADE");
        stmt0.close();
      }
      CloseDB();
      System.out.println("--FINISH CLEAR TABLE TEACHER--\n");
    } catch (SQLException e) {
      System.err.println("TEST Database connection failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }

    int n = jsonArray.size();
    long cnt = 0;

    //记录开始时间
    long start = System.currentTimeMillis();
    try {
      System.out.println("--START INSERT INTO TABLE TEACHER--");
      OpenDB();
      //准备导入数据库所需的jdbc::PreparedStatement, 以及对应的sql语句
      String sql_add_Teacher = "INSERT INTO teacher(id, classId, name) VALUES(DEFAULT,?,?)"
              + "ON conflict(classId) DO NOTHING;";
      create_PS(sql_add_Teacher);

      for (int i = 0; i < n; i++) {
        //从json文件中读取对应序号的信息，创造teacher对象，同时输入数据库中
        jsonToTeacher(i);
        cnt++;
        if (cnt % BATCH_SIZE == 0) {
          stmt.executeBatch();
          stmt.clearBatch();
          conn.commit();
        }
      }
      if (cnt % BATCH_SIZE != 0) {
        stmt.executeBatch();
        stmt.clearBatch();
        conn.commit();
      }
      CloseDB();
      System.out.println("--FINISH INSERT INTO TABLE TEACHER--\n");
      System.out.println(cnt + " records successfully loaded in table : TEACHER");
    } catch (SQLException se) {
      System.err.println("SQL error: " + se.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    }
    //记录结束时间
    long end = System.currentTimeMillis();
    System.out.println("Loading speed : " + (cnt * 1000)/(end - start) + " records/s");
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }
  //prerequisite数据的输入封装
  public static void insert_pre() {
    System.out.println("\033[33;4m" + "**************  START LOADING PREREQUISITE  **************" + "\033[0m");
    int n = jsonArray.size();
    long cnt = 0;

    //记录开始时间
    long start = System.currentTimeMillis();
    try {
      System.out.println("--START INSERT INTO TABLE PREREQUISITE--");
      OpenDB();
      //准备导入数据库所需的jdbc::PreparedStatement, 以及对应的sql语句
      String sql_add_Pre =
              "update course set requisiteCourseId = ? where id = ?";
      create_PS(sql_add_Pre);

      for (int i = 0; i < n; i++) {
        //将json文件中的数据导入对象中
        prerequisite prerequisite = new prerequisite();
        jsonToPre(i, prerequisite);
        //将prerequisite对象中的信息导入数据库中
        preToDatabase(prerequisite);
        cnt++;
        if (cnt % BATCH_SIZE == 0) {
          stmt.executeBatch();
          stmt.clearBatch();
          conn.commit();
        }
      }
      if (cnt % BATCH_SIZE != 0) {
        stmt.executeBatch();
        stmt.clearBatch();
        conn.commit();
      }
      CloseDB();
      System.out.println("--FINISH INSERT INTO TABLE PREREQUISITE--\n");
      System.out.println(cnt + " records successfully loaded in table : PREREQUISITE");
    } catch (SQLException se) {
      System.err.println("SQL error: " + se.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    }
    //记录结束时间
    long end = System.currentTimeMillis();
    System.out.println("Loading speed : " + (cnt * 1000)/(end - start) + " records/s");
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }
  //student数据的输入封装
  public static void insert_student(){
    System.out.println("\033[33;4m" + "**************  START LOADING STUDENT  **************" + "\033[0m");
    //清理原始数据,恢复自增列
    try {
      System.out.println("--START CLEAR TABLE STUDENT--");
      OpenDB();
      if (conn != null) {
        Statement stmt0 = conn.createStatement();
        stmt0.execute("TRUNCATE TABLE student RESTART IDENTITY CASCADE");
        stmt0.close();
      }
      CloseDB();
      System.out.println("--FINISH CLEAR TABLE STUDENT--\n");
    } catch (SQLException e) {
      System.err.println("TEST Database connection failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }

    long cnt = 0;
    long cnt_class = 0;
    String new_line;

    //记录开始时间
    long start = System.currentTimeMillis();
    long database_time = 0;
    try {
      System.out.println("--START INSERT INTO TABLE STUDENT--");
      OpenDB();
      //准备导入数据库所需的jdbc::PreparedStatement, 以及对应的sql语句
      String sql_add_student = "INSERT INTO student(id, name, sex, department, studentId) VALUES(?,?,?,?,?)";
      create_PS(sql_add_student);
      //String sql_add_studentClass = "INSERT INTO student_class(id, studentId, courseId) VALUES(DEFAULT,?,?)";
      //create_PS_2(sql_add_studentClass);

      while ((new_line = br.readLine()) != null && cnt <= 1000) {
        student student = new student();
        student_class student_class = new student_class();
        String[] info = new_line.split(",");
        student.setId((int) cnt);
        student.setName(info[0]);
        student.setSex(info[1]);
        student.setDepartment(info[2]);
        student.setStudentId(Integer.parseInt(info[3]));
        student_class.setStudentId(student.getId());
        for(int i = 4; i < info.length; i++)
        {
          student_class.setCourseId(info[i]);
          studentClassToDatabase(student_class);
          cnt_class++;
          if (cnt_class % BATCH_SIZE_2 == 0) {
            stmt2.executeBatch();
            stmt2.clearBatch();
            conn.commit();
          }
        }
        long time_1 = System.currentTimeMillis();
        studentToDatabase(student);
        long time_2 = System.currentTimeMillis();
        database_time += (time_2 - time_1);
        cnt++;
        if (cnt % BATCH_SIZE_2 == 0) {
          stmt.executeBatch();
          stmt.clearBatch();
          conn.commit();
        }
    }
      if (cnt % BATCH_SIZE_2 != 0) {
        stmt.executeBatch();
        stmt.clearBatch();
        conn.commit();
      }
      if (cnt_class % BATCH_SIZE_2 != 0) {
        stmt2.executeBatch();
        stmt2.clearBatch();
        conn.commit();
      }
      CloseDB();
      System.out.println("--FINISH INSERT INTO TABLE STUDENT--\n");
      System.out.println(cnt + " records successfully loaded in table : STUDENT");
    } catch (SQLException se) {
      System.err.println("SQL error: " + se.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    } catch (IOException e) {
      System.err.println("Fatal error: " + e.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    }
    //记录结束时间
    long end = System.currentTimeMillis();
    System.out.println("Loading speed : " + (cnt * 1000)/(end - start) + " records/s");
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }
  //classList数据的输入封装
  public static void insert_classList(){
    System.out.println("\033[33;4m" + "**************  START LOADING CLASSLIST  **************" + "\033[0m");
    //清理原始数据,恢复自增列
    try {
      System.out.println("--START CLEAR TABLE CLASSLIST--");
      OpenDB();
      if (conn != null) {
        Statement stmt0 = conn.createStatement();
        stmt0.execute("TRUNCATE TABLE classlist RESTART IDENTITY CASCADE");
        stmt0.close();
      }
      CloseDB();
      System.out.println("--FINISH CLEAR TABLE CLASSLIST--\n");
    } catch (SQLException e) {
      System.err.println("TEST Database connection failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }

    int n = jsonArray.size();
    long cnt = 0;

    //记录开始时间
    long start = System.currentTimeMillis();
    try {
      System.out.println("--START INSERT INTO TABLE CLASSLIST--");
      OpenDB();
      //准备导入数据库所需的jdbc::PreparedStatement, 以及对应的sql语句
      String sql_add_classList =
              "INSERT INTO classList(id, classId, weekList, location, classTime, weekday) VALUES(DEFAULT,?,?,?,?,?)";
      create_PS(sql_add_classList);

      for (int i = 0; i < n; i++) {
        //获取classname
        JsonObject jsonOBJ_courseArray = jsonArray.get(i).getAsJsonObject();
        String class_name = jsonOBJ_courseArray.get("className").getAsString();
        String course_Id = jsonOBJ_courseArray.get("courseId").getAsString();
        jsonArray_classList = jsonOBJ_courseArray.get("classList").getAsJsonArray();
        //同类似方法，创造classList对象，准备后续输入数据库中
        for (int j = 0; j < jsonArray_classList.size(); j++) {
          //从json文件中读取对应序号的信息，创造classList对象，准备后续输入数据库中
          classList classList = new classList();
          jsonToClassList(j, classList, class_name, course_Id);
          //将course_info对象中的信息导入数据库中
          classListToDatabase(classList);
          cnt++;
          if (cnt % BATCH_SIZE == 0) {
            stmt.executeBatch();
            stmt.clearBatch();
            conn.commit();
          }
        }
      }
      if (cnt % BATCH_SIZE != 0) {
        stmt.executeBatch();
        stmt.clearBatch();
        conn.commit();
      }
      CloseDB();
      System.out.println("--FINISH INSERT INTO TABLE CLASSLIST--\n");
      System.out.println(cnt + " records successfully loaded in table : CLASSLIST");
    } catch (SQLException se) {
      System.err.println("SQL error: " + se.getMessage());
      try {
        conn.rollback();
        stmt.close();
      } catch (Exception ignored) {
      }
      CloseDB();
      System.exit(1);
    }
    //记录结束时间
    long end = System.currentTimeMillis();
    System.out.println("Loading speed : " + (cnt * 1000)/(end - start) + " records/s");
    System.out.println("\033[32;4m" + "**************         SUCCESS         **************\n\n" + "\033[0m");
  }

  /***************************************数据库相关基础组件*********************************************/
  //将json文件中对应序号的数据读取并赋值给Java对象的组件
  private static void jsonToCourse(int index, course_info course_info, int id) {
    JsonObject jsonOBJ_courseArray = jsonArray.get(index).getAsJsonObject();
    course_info.setId(id);
    course_info.setCourseId(jsonOBJ_courseArray.get("courseId").getAsString());
    course_info.setCourseCredit(jsonOBJ_courseArray.get("courseCredit").getAsInt());
    course_info.setTotalCapacity(jsonOBJ_courseArray.get("totalCapacity").getAsInt());
    course_info.setCourseHour(jsonOBJ_courseArray.get("courseHour").getAsInt());
    course_info.setCourseDept(jsonOBJ_courseArray.get("courseDept").getAsString());
    course_info.setCourseName(jsonOBJ_courseArray.get("courseName").getAsString());
  }
  private static void jsonToClass(int index, class_info class_info, int id) {
    //通过json找出需要查询的string:::course_name, 并在hashMAP中找到对应的COURSE->ID
    JsonObject jsonOBJ_classArray = jsonArray.get(index).getAsJsonObject();
    String course_Id_real = jsonOBJ_classArray.get("courseId").getAsString();
    int courseId = MAP_course.get(course_Id_real);
    //将class_info对象中的信息插入数据库
    class_info.setId(id);
    class_info.setCourseId(courseId);
    class_info.setClassName(jsonOBJ_classArray.get("className").getAsString());
    class_info.setCourseName(jsonOBJ_classArray.get("courseName").getAsString());
    class_info.setCourseId_real(course_Id_real);
  }
  private static void jsonToTeacher(int index) throws SQLException {

    //通过json找出需要查询的string::course_name, 并在hashMAP中找到对应的CLASS->ID
    JsonObject jsonOBJ_classArray = jsonArray.get(index).getAsJsonObject();
    String class_Name = jsonOBJ_classArray.get("className").getAsString();
    String course_Id = jsonOBJ_classArray.get("courseId").getAsString();
    int classId = MAP_class.get(course_Id + class_Name);

    //将teacher对象中的信息插入数据库
    if (!jsonOBJ_classArray.get("teacher").equals(JsonNull.INSTANCE)) {
      String name = jsonOBJ_classArray.get("teacher").getAsString();
      String temp_name = name.replace(',', ' ');
      String[] name_arr = temp_name.split("\\s+");
      for (String s : name_arr) {
        teacher teacher = new teacher();
        teacher.setName(s);
        teacher.setClassId(classId);
        teacherToDatabase(teacher);
      }
    }
  }
  private static void jsonToPre(int index, prerequisite prerequisite) {
    JsonObject jsonOBJ_Pre = jsonArray.get(index).getAsJsonObject();
    String course_Id_1 = jsonOBJ_Pre.get("courseId").getAsString();

    int courseId = MAP_course.get(course_Id_1);
    prerequisite.setCourseId(courseId);

    // 检测先修课是否为空并且获取原始prerequisite字符串
    String pre;
    if (jsonOBJ_Pre.get("prerequisite").equals(JsonNull.INSTANCE)) {
      prerequisite.setPrerequisite_list(null);
      return;
    } else {
      pre = jsonOBJ_Pre.get("prerequisite").getAsString();
    }

    // 开始对字符串的转换处理模块
    // 以'|'分割
    String[] arr = pre.split("\\|");

    //替换加入最终数组中
    int lgh = arr.length;
    int[] fin_arr = new int[lgh];
    int t = 0;
    for (String s : arr) {
      switch (s) {
        case "(":
          fin_arr[t] = -3;
          break;
        case ")":
          fin_arr[t] = -4;
          break;
        case "或者":
          if (fin_arr[t] != -5) {
            fin_arr[t] = -1; // 或者 -> -1
          } else {
            t--;
          }
          break;
        case "并且":
          if (fin_arr[t] != -5) {
            fin_arr[t] = -2; // 并且 -> -2
          } else {
            t--;
          }
          break;
        default: {
          if (MAP_course_name.containsKey(s)) {
            int course_Id = MAP_course_name.get(s);
            fin_arr[t] = course_Id;
          } else {
            fin_arr[t] = -5;
            if (t >= 1 && (fin_arr[t - 1] == -1 || fin_arr[t - 1] == -2)) {
              fin_arr[t - 1] = -5;
            } else if (t <= lgh - 2) {
              fin_arr[t + 1] = -5;
            }
          }
          break;
        }
      }
      t++;
    }

    // 去重
    boolean[] dup = new boolean[100000];
    for (int i = 0; i < t; i++) {
      if (fin_arr[i] >= 0) {
        if (!dup[fin_arr[i]]) {
          dup[fin_arr[i]] = true;
        } else {
          fin_arr[i] = -5;
          if (fin_arr[i - 1] == -1 || fin_arr[i - 1] == -2) {
            fin_arr[i - 1] = -5;
          }
        }
      }
    }

    // 转化为后序表达式，转化之后的数组长度保存在变量ptr中
    int[] stack = new int[100];
    int top = 0, temp_int, ptr = 0;
    for (int i = 0; i < t; i++) {
      switch (fin_arr[i]) {
        case -1:
        case -2:
        case -3:
          stack[top++] = fin_arr[i];
          break;
        case -4:
          while (true) {
            temp_int = stack[--top];
            if (temp_int == -3) {
              break;
            } else {
              fin_arr[ptr++] = temp_int;
            }
          }
          break;
        case -5:
          break;
        default:
          fin_arr[ptr++] = fin_arr[i];
          break;
      }
    }
    while (top >= 1) {
      fin_arr[ptr++] = stack[--top];
    }

    // 数组转化为字符串，待储存
    StringBuilder final_str = new StringBuilder();
    for (int i = 0; i < ptr; i++) {
      final_str.append(fin_arr[i]);
      if (i != ptr - 1) {
        final_str.append("|");
      }
    }

    //将最终处理完成的字符串放入目标对象中
    prerequisite.setPrerequisite_list(final_str.toString());
  }
  private static void jsonToClassList(int index, classList classList, String class_Name, String course_Id) {
    int classId = MAP_class.get(course_Id+class_Name);
    JsonObject jsonOBJ_classList = jsonArray_classList.get(index).getAsJsonObject();

    classList.setClassId(classId);
    classList.setWeekList(jsonOBJ_classList.get("weekList").getAsJsonArray().toString());
    classList.setLocation(jsonOBJ_classList.get("location").getAsString());
    classList.setClassTime(jsonOBJ_classList.get("classTime").getAsString());
    classList.setWeekday(jsonOBJ_classList.get("weekday").getAsInt());
  }

  //将java对象中存储的数据导入数据库的组件
  private static void courseToDatabase(course_info course_info) throws SQLException {
    PreparedStatement ps_addCourse = stmt;
    ps_addCourse.setInt(1, course_info.getId());
    ps_addCourse.setInt(2, course_info.getTotalCapacity());
    ps_addCourse.setString(3, course_info.getCourseId());
    ps_addCourse.setInt(4, course_info.getCourseHour());
    ps_addCourse.setInt(5, course_info.getCourseCredit());
    ps_addCourse.setString(6, course_info.getCourseName());
    ps_addCourse.setString(7, course_info.getCourseDept());
    ps_addCourse.addBatch();
  }
  private static void classToDatabase(class_info class_info) throws SQLException {
    PreparedStatement ps_addClass = stmt;
    ps_addClass.setInt(1, class_info.getId());
    ps_addClass.setInt(2, class_info.getCourseId());
    ps_addClass.setString(3, class_info.getClassName());
    ps_addClass.addBatch();
  }
  private static void teacherToDatabase(teacher teacher) throws SQLException {
    PreparedStatement ps_addTeacher = stmt;
    ps_addTeacher.setInt(1, teacher.getClassId());
    ps_addTeacher.setString(2, teacher.getName());
    ps_addTeacher.addBatch();
  }
  public static void preToDatabase(prerequisite prerequisite) throws SQLException {
    PreparedStatement ps_addPre = stmt;
    ps_addPre.setInt(2, prerequisite.getCourseId());
    ps_addPre.setString(1, prerequisite.getPrerequisite_list());
    ps_addPre.addBatch();
  }
  public static void studentToDatabase(student student) throws SQLException {
    PreparedStatement ps_addTeacher = stmt;
    ps_addTeacher.setInt(1, student.getId());
    ps_addTeacher.setString(2, student.getName());
    ps_addTeacher.setString(3, student.getSex());
    ps_addTeacher.setString(4, student.getDepartment());
    ps_addTeacher.setInt(5, student.getStudentId());
    ps_addTeacher.addBatch();
  }
  public static void studentClassToDatabase(student_class student_class) throws SQLException {
    PreparedStatement ps_addTeacher = stmt2;
    ps_addTeacher.setInt(1, student_class.getStudentId());
    ps_addTeacher.setString(2, student_class.getCourseId());
    ps_addTeacher.addBatch();
  }
  private static void classListToDatabase(classList classList) throws SQLException {
    PreparedStatement ps_add_classList = stmt;
    ps_add_classList.setInt(1, classList.getClassId());
    ps_add_classList.setString(2, classList.getWeekList());
    ps_add_classList.setString(3, classList.getLocation());
    ps_add_classList.setString(4, classList.getClassTime());
    ps_add_classList.setInt(5, classList.getWeekday());
    ps_add_classList.addBatch();
  }

}