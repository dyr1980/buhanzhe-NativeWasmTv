package xiao.bu.tv;
import xiao.bu.tv.skin.TvUi;
/** Immutable loaded renderer; no Activity is retained by the loader cache. */
final class TvUiStyle {
    final String id;final TvUi provider;
    TvUiStyle(String id,TvUi provider){this.id=id;this.provider=provider;}
}
