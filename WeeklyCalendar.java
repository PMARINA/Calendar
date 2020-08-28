import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Scanner;

class WeeklyCalendar {
  /**
   * User Defined Constant: Days of the week... allows user to start week on
   * different days or include the weekend
   */
  public static final String[] days_of_the_week = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday" };
  /**
   * Text file where the events are stored... can be relative or absolute path
   */
  public static final String INPUT_FILENAME = "ExampleInput";
  public static final String OUTPUT_EXTENSION = ".png";
  /**
   * User Defined Constant: DPI = number of pixels in an inch. Larger DPI makes
   * the generator take more time, but gives sharper result for the same page size
   */
  private static final int DPI = 200;
  /**
   * Width of the paper in inches... if user wishes to flip the calendar to be
   * vertical, user must only swap the values for paper_width_inches and
   * paper_height_inches
   */
  private static final double PAPER_WIDTH_INCHES = 11;
  private static final double PAPER_HEIGHT_INCHES = 8.5;
  private static final double MARGIN_X_INCHES = 0.1;
  private static final double MARGIN_Y_INCHES = 0.1;

  /** A constant representing font size... 0.02 is about 12 pt font */
  private static final double FONT_FRACTION = 0.02;

  // End user-defined variables -------------------------------------------------
  public static final String OUTPUT_FILENAME = INPUT_FILENAME + OUTPUT_EXTENSION;
  /**
   * Constant based on the days of the week, should not need to be modified.
   */
  public static final int NUM_DAYS_WEEK = days_of_the_week.length;
  public static Color BACKGROUND_COLOR;
  public static Color HEADER_COLOR;
  private static final double OUTPUT_WIDTH_PIXELS = PAPER_WIDTH_INCHES * DPI;
  private static final double PRINTABLE_WIDTH_PIXELS = OUTPUT_WIDTH_PIXELS - DPI * MARGIN_X_INCHES * 2;
  private static final double PAPER_WIDTH_FRACTION_PRINTABLE = PRINTABLE_WIDTH_PIXELS / OUTPUT_WIDTH_PIXELS;
  private static final double WIDTH_COLUMN_FRACTION = PRINTABLE_WIDTH_PIXELS / (NUM_DAYS_WEEK) / PRINTABLE_WIDTH_PIXELS;
  private static final double HALF_WIDTH_COLUMN_FRACTION = WIDTH_COLUMN_FRACTION / 2.0;

  /**
   * The fraction of the page that is not to be printed on (and therefore will be
   * above 1 or less than 0)
   */
  private static final double X_OVERRUN_EACH_SIDE = 0.5 - 0.5 * PAPER_WIDTH_FRACTION_PRINTABLE;

  private static final double OUTPUT_HEIGHT_PIXELS = PAPER_HEIGHT_INCHES * DPI;
  private static final double PRINTABLE_HEIGHT_PIXELS = OUTPUT_HEIGHT_PIXELS - DPI * MARGIN_Y_INCHES * 2;
  private static final double PAPER_HEIGHT_FRACTION_PRINTABLE = PRINTABLE_HEIGHT_PIXELS / OUTPUT_HEIGHT_PIXELS;
  /**
   * The fraction of the page that is not to be printed on (and therefore will be
   * above 1 or less than 0)
   */
  private static final double Y_OVERRUN_EACH_SIDE = 0.5 - 0.5 * PAPER_HEIGHT_FRACTION_PRINTABLE;

  /** The font size in pixels */
  private static final int FONT_SIZE_PIXELS = (int) (FONT_FRACTION * OUTPUT_HEIGHT_PIXELS);
  private static final double FONT_SIZE_FRACTION = FONT_SIZE_PIXELS / OUTPUT_HEIGHT_PIXELS;
  private static final double HEADER_FRACTION = 2 * FONT_SIZE_FRACTION;
  private static final int MINUTES_IN_A_DAY = 60 * 24;

  /** Earliest event in the week, in minutes since midnight */
  protected static int earliest_event_in_day = MINUTES_IN_A_DAY;
  /** Latest event in the week, in minutes since midnight */
  protected static int latest_event_in_day = 0;
  /**
   * Number of minutes between the earliest event in the week, and the latest
   * event. Needs to be updated using synchronize_time_in_day()
   */
  private static int minutes_represented_in_calendar_day = MINUTES_IN_A_DAY;

  /** A list of events to be stored in the calendar */
  private static List<Event> events = new ArrayList<Event>();
  /** A map of categories to colors */
  private static final Map<String, ColorCombo> colormap = new HashMap<String, ColorCombo>();

  public static void main(final String[] args) throws FileNotFoundException {
    setup_std_draw();
    parse_inputfile_for_events();
    render_calendar();

    save_calendar();
    System.exit(0);
  }

  private static void save_calendar() {
    StdDraw.save(OUTPUT_FILENAME);
  }

  private static void setup_std_draw() {
    StdDraw.setCanvasSize((int) (OUTPUT_WIDTH_PIXELS), (int) (OUTPUT_HEIGHT_PIXELS));
    StdDraw.setXscale(0 - X_OVERRUN_EACH_SIDE, 1 + X_OVERRUN_EACH_SIDE);
    StdDraw.setYscale(0 - Y_OVERRUN_EACH_SIDE, 1 + Y_OVERRUN_EACH_SIDE);
    StdDraw.setFont(new Font("Times New Roman", Font.PLAIN, FONT_SIZE_PIXELS));
    StdDraw.setPenRadius(0.05 / 600 * DPI);
  }

  public static void parse_inputfile_for_events() throws FileNotFoundException {
    final File f = new File(INPUT_FILENAME);
    final Scanner sc = new Scanner(f);
    String[] paper_color = sc.nextLine().strip().split("#");
    String backgroundPaperColor = paper_color[2].strip();
    String foregroundPaperColor = paper_color[1].strip();
    BACKGROUND_COLOR = Color.decode(backgroundPaperColor);
    HEADER_COLOR = Color.decode(foregroundPaperColor);
    String colorCode = sc.nextLine();
    while (!colorCode.equals("")) {
      final String category = colorCode.split(" ")[0].strip();
      Color fg = Color.BLACK, bg = Color.WHITE;
      try {
        fg = Color.decode(colorCode.split("#")[1].strip());
        bg = Color.decode(colorCode.split("#")[2].strip());
      } catch (NumberFormatException e) {
        System.out.println("Failed to read color of category " + category);
        e.printStackTrace();
        System.exit(1);
      }
      colormap.put(category, new ColorCombo(fg, bg));
      colorCode = sc.nextLine();
    }
    while (sc.hasNextLine()) {
      String eventName = null;
      while (sc.hasNextLine() && (eventName == null || eventName.equals(""))) {
        eventName = sc.nextLine();
      }
      if (!sc.hasNextLine())
        break;
      final Event event = new Event(eventName);
      final ColorCombo cc = colormap.get(sc.nextLine());
      event.setColorCombo(cc);
      final int numOcc = Integer.parseInt(sc.nextLine());
      for (int i = 0; i < numOcc; i++) {
        // Read in the occurrence
        final String occ = sc.nextLine();

        // Split into occ fields
        final String dayOfWeek = occ.split(" ")[0].strip();
        final Integer start = Integer.parseInt(occ.split(" ")[1].strip());
        final Integer end = Integer.parseInt(occ.split(" ")[2].strip());
        // create object
        final DateTime dt = new DateTime(dayOfWeek, start, end);
        // add occurrence to event
        event.addOcc(dt);
      }
      // When done, add the event to the list...
      events.add(event);
    }
    sc.close();
  }

  static void draw_events() {
    for (final Event e : events) {
      draw_event(e);
    }
  }

  protected static void render_calendar() {
    StdDraw.clear(BACKGROUND_COLOR);
    synchronize_time_in_day();
    draw_events();
    draw_columns();
    draw_header();
  }

  private static void draw_header() {
    StdDraw.setPenColor(HEADER_COLOR);
    for (int i = 0; i < NUM_DAYS_WEEK; i++) {
      StdDraw.text(get_x_mid_fraction(i), 1 - HEADER_FRACTION / 2.0, days_of_the_week[i]);
    }
    StdDraw.line(0, 1 - HEADER_FRACTION, 1, 1 - HEADER_FRACTION);
  }

  private static void draw_columns() {
    StdDraw.setPenColor(HEADER_COLOR);
    for (int i = 0; i < NUM_DAYS_WEEK; i++) {
      // Draw the left-border
      StdDraw.line((double) i / NUM_DAYS_WEEK, 0, (double) i / NUM_DAYS_WEEK, 1);
    }
    StdDraw.line(1, 0, 1, 1);
  }

  static void draw_event(Event e) {
    for (DateTime dt : e.occurrences) {
      drawOcc(e, dt);
    }
  }

  static void drawOcc(Event e, DateTime d) {
    final int startMin = military_to_min(d.startTime);
    final int endMin = military_to_min(d.endTime);
    StdDraw.setPenColor(e.cc.bg);
    drawBoundingBox(d.dayNum, startMin, endMin);
    StdDraw.setPenColor(e.cc.fg);
    drawOccText(e, d);
  }

  static void drawOccText(Event e, DateTime d) {
    final int startMin = military_to_min(d.startTime);
    final int endMin = military_to_min(d.endTime);
    double x_mid = get_x_mid_fraction(d.dayNum);
    double y_mid = get_y_mid_fraction(startMin, endMin);
    StdDraw.text(x_mid, y_mid + 0.5 * FONT_SIZE_FRACTION, e.name);
    String time_string = d.time_to_string();
    StdDraw.text(x_mid, y_mid - 0.5 * FONT_SIZE_FRACTION, time_string);
  }

  static void drawBoundingBox(int day, int startmin, int endmin) {
    double x_mid = get_x_mid_fraction(day);
    synchronize_time_in_day();
    double y_mid = get_y_mid_fraction(startmin, endmin);
    double length_of_event_fraction = get_length_of_event_fraction(startmin, endmin);
    StdDraw.filledRectangle(x_mid, y_mid, WIDTH_COLUMN_FRACTION / 2, length_of_event_fraction / 2);
  }

  static double get_length_of_event_fraction(int startmin, int endmin) {
    double fraction_of_timebar = (double) (endmin - startmin) / minutes_represented_in_calendar_day;
    final double HEIGHT_TIMEBAR = 1 - HEADER_FRACTION;
    return fraction_of_timebar * HEIGHT_TIMEBAR;
  }

  static double get_y_mid_fraction(int startmin, int endmin) {
    double timebar_fraction = 1
        - ((double) ((startmin + endmin) / 2.0 - earliest_event_in_day) / minutes_represented_in_calendar_day);
    return timebar_fraction * (1 - HEADER_FRACTION);
  }

  static double get_x_mid_fraction(int day) {
    return (double) day * WIDTH_COLUMN_FRACTION + (double) HALF_WIDTH_COLUMN_FRACTION;
  }

  static void synchronize_time_in_day() {
    minutes_represented_in_calendar_day = latest_event_in_day - earliest_event_in_day;
    if (minutes_represented_in_calendar_day < 0)
      minutes_represented_in_calendar_day = MINUTES_IN_A_DAY;
  }

  static int military_to_min(int mil_time) {
    return (mil_time / 100) * 60 + mil_time % 100;
  }
}

class Event {
  String name;
  ColorCombo cc;
  List<DateTime> occurrences = new ArrayList<DateTime>();

  Event(final String name) {
    this.name = name;
  }

  Event(final String name, final DateTime[] occs) {
    this.name = name;
    occurrences = Arrays.asList(occs);
  }

  void addOcc(final DateTime dt) {
    occurrences.add(dt);
  }

  void setColorCombo(ColorCombo cc) {
    this.cc = cc;
  }
}

class DateTime {
  int dayNum = -1;
  int startTime = -1;
  int endTime = -1;

  DateTime(final int dayNum, final int startTime, final int endTime) {
    this.dayNum = dayNum;
    this.startTime = startTime;
    this.endTime = endTime;
    update_weeklycalendar_class_limits();
  }

  DateTime(final String day, final int start, final int end) {
    for (int i = 0; i < WeeklyCalendar.NUM_DAYS_WEEK; i++) {
      if (WeeklyCalendar.days_of_the_week[i].equals(day)) {
        dayNum = i;
        break;
      }
    }
    startTime = start;
    endTime = end;
    update_weeklycalendar_class_limits();
  }

  void update_weeklycalendar_class_limits() {
    final int start = WeeklyCalendar.military_to_min(startTime);
    final int end = WeeklyCalendar.military_to_min(endTime);
    if (start < WeeklyCalendar.earliest_event_in_day)
      WeeklyCalendar.earliest_event_in_day = start;
    if (end > WeeklyCalendar.latest_event_in_day)
      WeeklyCalendar.latest_event_in_day = end;
  }

  String time_to_string() {
    String time = "";
    time += startTime / 100;
    if (startTime / 100 < 10)
      time = "0" + time;
    time += ":";
    time += startTime % 100;
    if (startTime % 100 < 10)
      time += "0";
    time += " - ";
    time += endTime / 100;
    if (endTime / 100 < 10)
      time = "0" + time;
    time += ":";
    time += endTime % 100;
    if (endTime % 100 < 10)
      time += "0";
    return time;
  }
}

class ColorCombo {
  Color fg;
  Color bg;

  public ColorCombo(Color fg, Color bg) {
    this.fg = fg;
    this.bg = bg;
  }

}