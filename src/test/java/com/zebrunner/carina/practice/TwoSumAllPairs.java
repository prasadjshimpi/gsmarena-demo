package com.zebrunner.carina.practice;
import java.util.ArrayList;
import java.util.List;

public class TwoSumAllPairs {
    public static void main(String[] args) {
        int[] nums = {6, 8, 11, 6, 7, 18, 10};
        int target = 17;

        List<int[]> result = findAllTwoSumPairs(nums, target);

        if (!result.isEmpty()) {
            System.out.println("Total pairs found: " + result.size());
            for (int[] pair : result) {
                System.out.println("Indices: " + pair[0] + " and " + pair[1] +
                        " => Values: " + nums[pair[0]] + " + " + nums[pair[1]]);
            }
        } else {
            System.out.println("No pairs found with sum " + target);
        }
    }

    public static List<int[]> findAllTwoSumPairs(int[] nums, int target) {
        List<int[]> result = new ArrayList<>();

        // Use a nested loop to check all unique pairs
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    result.add(new int[]{i, j});
                }
            }
        }

        return result;
    }
}
