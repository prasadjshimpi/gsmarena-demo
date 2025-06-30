package com.zebrunner.carina.practice;
import java.util.HashMap;
import java.util.Map;

public class TwoSum {
    public static void main(String[] args) {
        int[] nums = {6, 8, 11, 6, 7, 18,10};
        int target = 17;

        int[] result = findTwoSum(nums, target);

        if (result != null) {
            System.out.println("Indices: " + result[0] + " and " + result[1]);
        } else {
            System.out.println("No two elements found with sum " + target);
        }
    }

    public static int[] findTwoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>(); // number -> index

        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];

            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }

            map.put(nums[i], i);
        }

        return null; // not found
    }
}
