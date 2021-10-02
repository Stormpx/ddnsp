package io.crowds;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strs {

    public static boolean isBlank(String str){
        return str==null||str.isBlank();
    }

    public static boolean isLen(String str,int s,int e){
        return str!=null&&str.length()>=s&&str.length()<=e;
    }
    public static boolean isUri(String str){
        if (isBlank(str))
            return false;
        try {
            new URI(str);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static boolean isPositiveInt(String str){
        if (isBlank(str))
            return false;
        char[] chars = str.toCharArray();
        for (char c : chars) {
            if (!Character.isDigit(c))
                return false;
        }
        return true;
    }

    public static int commonPrefixLen(String s1,String s2){
        int j=0;
        for(; j< s1.length() && j < s2.length(); j++) {
            if(s1.charAt(j) != s2.charAt(j))
                break;
        }

        return j;
    }


    public static String template(String str, Map<String,String> map){
        if (map==null)
            return str;

        String patternStr="(\\$\\{[\\s\\S]*?\\})";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(str);

        return matcher.replaceAll(r->{
//            System.out.println(r.start());
            String group = r.group();
            if (group.length()<=3)
                return "";
            String substring = group.substring(2, group.length() - 1).trim();

            String s = map.get(substring);
            if (s==null)
                return "";

            return s;
        });
    }

}
