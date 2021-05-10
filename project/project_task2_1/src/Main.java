public class Main {
    public static void main(String[] args){
        /*// 参数：
        // jdbc协议:postgresql子协议://主机地址:数据库端口号/要连接的数据库名
        String database_name = "test_fei";
        String host = "121.4.163.50:10943";
        // 数据库用户名
        String user = "postgres";
        // 数据库密码
        String password = "What1sth1s";*/

        // 参数：
        // jdbc协议:postgresql子协议://主机地址:数据库端口号/要连接的数据库名
        String database_name = "postgres";
        String host = "localhost:5432";
        // 数据库用户名
        String user = "postgres";
        // 数据库密码
        String password = "20011127qcf";

        //两个目标文件的地址
        String fileName_JSON = "./source_data/output.json";
        String fileName_CSV = "./source_data/select_course.csv";

        //传入开启数据库所需数据，测试开启关闭数据库
        connect.testConnection(host, database_name, user, password);

        //解析两个文件
        connect.loadFromJson(fileName_JSON);
        connect.loadFromCSV(fileName_CSV);

        //connect.insert_Course();    //course表中数据的输入
        //connect.insert_Class();     //class表中数据的输入
        //connect.insert_pre();       //先修课输入 转换成数字及符号
        //connect.insert_teacher();   //teacher表中数据的输入
        //connect.insert_classList(); //classList数据输入
        //connect.insert_student();   //student表中数据的输入


    }
}
