public class classList {

  private int id;
  private int classId;
  private String weekList;
  private String location;
  private String classTime;
  private int weekday;

  public classList(){}

  public classList(int id, int classId, String weekList, String location, String classTime,
      int weekday) {
    this.id = id;
    this.classId = classId;
    this.weekList = weekList;
    this.location = location;
    this.classTime = classTime;
    this.weekday = weekday;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getClassId() {
    return classId;
  }

  public void setClassId(int classId) {
    this.classId = classId;
  }

  public String getWeekList() {
    return weekList;
  }

  public void setWeekList(String weekList) {
    this.weekList = weekList;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getClassTime() {
    return classTime;
  }

  public void setClassTime(String classTime) {
    this.classTime = classTime;
  }

  public int getWeekday() {
    return weekday;
  }

  public void setWeekday(int weekday) {
    this.weekday = weekday;
  }
}
