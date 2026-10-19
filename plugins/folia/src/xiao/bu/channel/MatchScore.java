package xiao.bu.channel;

import java.util.*;

/** Simplified Java adaptation of Folia matchScore.ts (AGPL-3.0); see NOTICE. */
final class MatchScore {
    static String normalize(String s){return s.toLowerCase(Locale.US).replaceAll("[\\p{P}\\p{S}\\s]","");}
    static double similarity(String a,String b) {
        a=normalize(a);b=normalize(b);if(a.length()==0||b.length()==0)return 0;
        if(a.equals(b))return 1;
        if(a.contains(b)||b.contains(a))return (double)Math.min(a.length(),b.length())/Math.max(a.length(),b.length());
        Set<Character> left=new HashSet<>(),right=new HashSet<>();
        for(char c:a.toCharArray())left.add(c);for(char c:b.toCharArray())right.add(c);
        Set<Character> union=new HashSet<>(left);union.addAll(right);left.retainAll(right);
        return (double)left.size()/union.size();
    }
    static double score(String title,String artist,String candidate,String artists,long duration,long candidateDuration) {
        double t=similarity(title,candidate),a=artist.trim().length()==0?1:similarity(artist,artists);
        double score=t*45+a*25+30;
        if(t<0.65||a<0.5)score=Math.min(74,score);
        if(duration>0&&candidateDuration>0){long d=Math.abs(duration-candidateDuration);score*=d<=1000?1:d<=3000?.95:d<=5000?.75:d<=10000?.35:.1;}
        return score;
    }
}
