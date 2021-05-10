public class class_info {

  private int id;
  private int courseId;
  private String className;
  private String teacher;
  private String courseName;
  private String courseId_real;

  public class_info() {}

  public String getCourseId_real() {
    return courseId_real;
  }

  public void setCourseId_real(String courseId_real) {
    this.courseId_real = courseId_real;
  }

  public String getCourseName() {
    return courseName;
  }

  public void setCourseName(String courseName) {
    this.courseName = courseName;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getCourseId() {
    return courseId;
  }

  public void setCourseId(int courseId) {
    this.courseId = courseId;
  }

  public String getClassName() {
    return this.className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getTeacher() {
    return this.teacher;
  }

  public void setTeacher(String teacher) {
    this.teacher = teacher;
  }
}
