import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.jsonwebtoken.lang.Arrays;

public class Test {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }

    public int[] twoSum(int[] nums, int target) {
        int left = 0, right = nums.length - 1;
        while (left < right) {
            int sum = nums[left] + nums[right];
            if (sum == target) {
                return new int[] { left, right };
            } else if (sum < target) {
                left++;
            } else {
                right--;
            }
        }
        return new int[] { -1, -1 };
    }

    // Input: nums = [1, 1, 2, 2, 3, 4, 4]
    // Output: 5 (array becomes [1, 2, 3, 4, _], where _ is don't care)
    public int removeDuplicates(int[] nums) {
        int writePos = 1;
        for (int readPos = 1; readPos < nums.length; readPos++) {
            if (nums[readPos] != nums[readPos - 1]) {
                nums[writePos] = nums[readPos];
                writePos++;
            }
        }
        return writePos;
    }

    /*
     * Problem: Find the first non-repeating character in a string.
     * 
     * Example:
     * 
     * Copied!
     * Input: s = "leetcode"
     * Output: 'l' (appears once, first non-repeating char)
     * 
     * Input: s = "loveleetcode"
     * Output: 'v'
     */

    public char firstUniqChar(String s) {
        var map = new HashMap<Character, Integer>();

        // Step 1: Count frequencies
        for (char c : s.toCharArray()) {
            map.put(c, map.getOrDefault(c, 0) + 1);
        }

        for (char c : s.toCharArray()) {
            if (map.get(c) == 1) {
                return c;
            }
        }
        return ' ';
    }

    /*
     * Problem: Find all bigrams in a sentence.
     * 
     * A bigram is a pair of consecutive words.
     * 
     * Example:
     * 
     * Copied!
     * Input: "I love coding interviews"
     * Output: ["I love", "love coding", "coding interviews"]
     * 
     */

    public List<String> findBigrams(String sentence) {
        List<String> bigrams = new ArrayList<>();
        String[] words = sentence.split(" ");
        for (int i = 1; i <= words.length - 1; i++) {
            bigrams.add(words[i - 1] + " " + words[i]);
        }
        return bigrams;
    }

    public List<Map<String, Integer>> findBigramsWithSameFirstLetter(String sentence) {
        List<Map<String, Integer>> bigrams = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        String[] words = sentence.split(" ");

        for (int i = 1; i <= words.length - 1; i++) {
            var firstLetter = words[i - 1].charAt(0);
            var secondLetter = words[i].charAt(0);
            if (firstLetter == secondLetter) {
                var key = words[i - 1] + " " + words[i];
                map.put(key, map.getOrDefault(key, 0) + 1);
            }
            bigrams.add(map);
        }
        return bigrams;
    }

    private Boolean isAnagram(String s1, String s2) {
        Map<Character, Integer> map = new HashMap<>()
        if (s1.length() != s2.length()) {
            return false;
        }
        for(char c : s1.toCharArray()) {
            map.put(c, map.getOrDefault(c, 0) + 1);
        }
        for(char c : s2.toCharArray()) {
            map.put(c, map.getOrDefault(c, 0) - 1);
        }
        for(int count : map.values()) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 
     * Input: ["eat", "tea", "tan", "ate", "nat", "bat"]
     * Output: [["eat", "tea", "ate"], ["tan", "nat"], ["bat"]]
     */

    public List<List<String>> groupAnagrams(List<String> strs) {
        var map = new HashMap<String, List<String>>();

        for (String str : strs) {
            char[] chars = str.toCharArray();
            Arrays.sort(chars);
            var newString = new String(chars);
            map.putIfAbsent(newString, strs);
            map.get(newString).add(str);
        }

        return new ArrayList<>(map.values());
    }

    // Input: s = "cbaebabacd", pattern = "abc"
    // Output: [0, 6]
    public List<Integer> findAnagrams(String str, String pattern) {
        List<Integer> result = new ArrayList<>();
        char[] patternChars = pattern.toCharArray();
        Map<String, Integer> map = new HashMap<>();
        var patternKey = Arrays.sort(pattern);
        var len = pattern.length();
        for (int i = len; i < str.length(); i++) {
            for (int j = i - len; j < len; j++) {
                var key = Arrays.sort(str.substring(j, j + len));
                if (key.equals(pattern)) {
                    result.add(j);
                }
            }
        }
        return result;
    }

    public Boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }
}