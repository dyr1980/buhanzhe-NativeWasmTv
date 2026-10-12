"""Experimental reusable CMG UpdatePlayer workspace; preserve every update."""
import argparse
from pathlib import Path


def patch(source):
    if 'static u32 g_update_workspace_address;' in source:
        return source
    pairs = [
        ('static u32 g_decode_tag_capacity;', 'static u32 g_decode_tag_capacity;\nstatic u32 g_update_workspace_address;\nstatic u32 g_update_workspace_capacity;'),
        ('  g_decode_tag_capacity = 0;', '  g_decode_tag_capacity = 0;\n  g_update_workspace_address = 0;\n  g_update_workspace_capacity = 0;'),
        ('  tag_address = cmg_write_tag_buffer(g_player_tag, CMG_MEMORY_EXTEND, 1);', '''  size_t workspace_size = strlen(g_player_tag) + CMG_MEMORY_EXTEND;
  uint8_t* workspace;
  if (g_update_workspace_address == 0 || g_update_workspace_capacity < workspace_size) {
    if (g_update_workspace_address != 0) (*Z_BaZ_vi)(g_update_workspace_address);
    g_update_workspace_address = (*Z_CaZ_ii)((u32) workspace_size);
    g_update_workspace_capacity = (u32) workspace_size;
  }
  tag_address = g_update_workspace_address;
  workspace = cmg_pointer(tag_address, workspace_size);
  if (workspace != NULL) {
    memset(workspace, 0, workspace_size);
    memcpy(workspace, g_player_tag, strlen(g_player_tag));
  }'''),
        ('''  if (tag_address != 0) {
    (*Z_BaZ_vi)(tag_address);
  }
  if (should_log) {
    LOGI("CMG UpdatePlayer free tagAddr=%u top=%u", tag_address, cmg_dynamic_top());
  }''', '  /* Workspace belongs to this runtime and is cleared before each update. */'),
    ]
    for old, new in pairs:
        if source.count(old) != 1:
            raise ValueError('Unexpected source: ' + old[:80])
        source = source.replace(old, new)
    return source


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.write_text(patch(args.source.read_text(encoding='utf-8')), encoding='utf-8')
