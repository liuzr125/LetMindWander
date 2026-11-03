package com.zhixing.books;

import java.util.*;

/** Draft and published snapshots have separate storage; chapter/paragraph IDs survive editing. */
public class BookDocument {
  public String title = "", author = "", edition = "", difficulty = "intro", summary = "";
  public String kind = "licensed", sourceUrl = "", licenseNote = "", rightsScope = "";
  public boolean rightsConfirmed,
      audioRightsConfirmed,
      imagesRightsConfirmed,
      completenessConfirmed;
  public String voice = "luca", artStyle = "warm watercolor storybook", characterGuide = "";
  public List<Chapter> chapters = new ArrayList<>();

  public static class Chapter {
    public String id = UUID.randomUUID().toString().replace("-", ""), title = "";
    public String audioGeneration = "v1", imageGeneration = "v1";
    public List<Paragraph> paragraphs = new ArrayList<>();
    public List<Picture> pictures = new ArrayList<>();
    public List<Clip> audio = new ArrayList<>();
  }

  public static class Paragraph {
    public String id = UUID.randomUUID().toString().replace("-", ""), text = "", translation = "";
  }

  public static class Picture {
    public String assetId = "", caption = "", paragraphId = "", source = "ai", licenseNote = "";
  }

  public static class Clip {
    public double durationSeconds;
    public String key = "", assetId = "", paragraphId = "", text = "", voice = "";
  }
}
