package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

public class TextNodeStatistics {
    private final ModeWeightStatistics fontSizeStatistics;
    private final ModeWeightStatistics fontWeightStatistics;
    private final TextNodeStatisticsConfig config;

    public TextNodeStatistics() {
        this(new TextNodeStatisticsConfig());
    }

    public TextNodeStatistics(TextNodeStatisticsConfig config) {
        this.config = config;

        double sizeScoreMin = config.fontSizeHeadingMin;
        double sizeScoreMax = config.fontSizeHeadingMax;
        double sizeModeMin = config.fontSizeDominantMin;
        double sizeModeMax = config.fontSizeDominantMax;
        fontSizeStatistics = new ModeWeightStatistics(sizeScoreMin, sizeScoreMax, sizeModeMin, sizeModeMax);

        double weightScoreMin = config.fontWeightHeadingMin;
        double weightScoreMax = config.fontWeightHeadingMax;
        double weightModeMin = config.fontWeightDominantMin;
        double weightModeMax = config.fontWeightDominantMax;
        fontWeightStatistics = new ModeWeightStatistics(weightScoreMin, weightScoreMax, weightModeMin, weightModeMax);
    }

    public void addTextNode(SemanticTextNode textNode) {
        if (textNode == null) {
            return;
        }
        fontSizeStatistics.addScore(textNode.getFontSize());
        fontWeightStatistics.addScore(textNode.getFontWeight());
    }

    public double fontSizeRarityBoost(SemanticTextNode textNode) {
        double boost = fontSizeStatistics.getBoost(textNode.getFontSize());
        return boost * config.fontSizeRarityBoost;
    }

    public double fontWeightRarityBoost(SemanticTextNode textNode) {
        double boost = fontWeightStatistics.getBoost(textNode.getFontWeight());
        return boost * config.fontWeightRarityBoost;
    }

    /**
     * @return the font size of ordinary body text, or 0.0 when no text node was added
     */
    public double getDominantFontSize() {
        return fontSizeStatistics.getDominantScore();
    }

    /**
     * @return the font weight of ordinary body text, or 0.0 when no text node was added
     */
    public double getDominantFontWeight() {
        return fontWeightStatistics.getDominantScore();
    }
}
