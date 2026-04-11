package org.example;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AIManager {
    private final Random random = new Random();

    public String getResponse(YamlConfiguration config, String userInput) {
        if (config == null) return null;

        // Lấy ngưỡng chính xác (mặc định 60%)
        double threshold = config.getDouble("Exactly", 60.0) / 100.0;
        ConfigurationSection chatSec = config.getConfigurationSection("Chat");
        if (chatSec == null) return null;

        ConfigurationSection inputSec = chatSec.getConfigurationSection("Input");
        if (inputSec == null) return null;

        for (String groupKey : inputSec.getKeys(false)) {
            List<String> samples = inputSec.getStringList(groupKey);
            for (String sample : samples) {
                if (getSimilarity(userInput, sample) >= threshold) {
                    List<String> outputs = chatSec.getStringList("Output." + groupKey);
                    if (outputs != null && !outputs.isEmpty()) {
                        return outputs.get(random.nextInt(outputs.size()));
                    }
                }
            }
        }
        return null;
    }

    // Thuật toán Levenshtein tính độ tương đồng 0.0 -> 1.0
    private double getSimilarity(String s1, String s2) {
        String longer = s1.toLowerCase(), shorter = s2.toLowerCase();
        if (s1.length() < s2.length()) { longer = s2; shorter = s1; }
        int longerLength = longer.length();
        if (longerLength == 0) return 1.0;
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    private int editDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1))
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}