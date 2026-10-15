"""Validate volume reflection targets and original animation calls against a captured SystemUI APK.
Requires androguard; no device connection or vendor source redistribution is needed.
"""
from argparse import ArgumentParser
from zipfile import ZipFile
from loguru import logger
from androguard.core.dex import DEX

parser = ArgumentParser(description=__doc__)
parser.add_argument('apk', help='Captured ColorOS 17 SystemUI.apk')
args = parser.parse_args()
logger.remove()
base = 'Lcom/oplus/systemui/volume/'
required = {
    base+'view/OplusVolumeDialogView;': (
        {'initDialog', 'updateRowsH', 'showSliderVolume', 'updateState', 'adaptThemeDrawable', 'expandPanel', 'resetSliderState','getIconHalfDelta','startBtnThickAnim'},
        {'volumeInteractor','volumeListAdapter','mDialogView','mVolumeRecyclerview','mSettingsButton','mMoreRowStreamLl','mAppVolumeAdjustFl','mODICaptionsView','mVolumePanelTitleIcon','mVolumePanelTitleText','mVolumePanelTipsText','mVolumeSettingText','mSnackBar'}),
    base+'view/OplusVolumeDialogView$CustomDialog;': ({'dispatchTouchEvent'}, {'this$0'}),
    base+'OplusVolumeDialogImpl;': ({'getActiveRow','dismissH$1','destroy','onConfigChanged','onThemeChanged','onUiModeChanged'}, {'mRows','mOplusVolumeDialogView'}),
    base+'view/OplusVolumeRow;': (set(), {'slider','sliderFrame','view'}),
    base+'VolumeListAdapter;': ({'switchToDataSourcePanel','onBindViewHolder','getPanelShowList'}, set()),
    base+'anim/VolumeItemAnimator;': ({'getEndX','getTranslationY','resolvePanelStartTranslationX','animateAddImpl','animateMoveImpl','runPendingAnimations'}, {'adapter','cachedPanelBiasTranslationY'}),
    base+'utils/material/VolumePlatformBlurHostHelper;': ({'mountPanelBackground','createPanelBackgroundIfNeeded','applyBarPanelBottomProgress'}, {'deps'}),
    base+'utils/material/VolumeBlurManager;': ({'getVolumeBarBackground','getVolumeSettingsButtonBackground','getVolumeSideAccessoryBackground','getVolumePanelBackground'}, set()),
    base+'utils/material/OplusVolumeBlurDrawableFinder;': ({'findAutoBlurDrawable'}, set()),
    base+'OplusVolumeSeekBar;': ({'onAttachedToWindow','onMeasure','onDraw','draw','preCalcMaterialClipPath'}, set()),
    'Lcom/coui/appcompat/seekbar/COUIVerticalSeekBar;': ({'ensureSize','updatePixPerProgress'}, {'mBackgroundRect','mBackgroundWidth','mCurBackgroundWidth','mProgressWidth','mCurProgressWidth','mBackgroundRadius','mCurBackgroundRadius','mProgressRadius','mCurProgressRadius','mPaddingVertical','mCurPaddingVertical','mSeekbarMinWidth','mBackgroundColor'}),
}
classes = {}
with ZipFile(args.apk) as apk:
    for entry in apk.namelist():
        if entry.endswith('.dex'):
            dex = DEX(apk.read(entry))
            for cls in dex.get_classes():
                if cls.get_name() in required: classes[cls.get_name()] = cls
count = 0
for name, (methods, fields) in required.items():
    assert name in classes, f'Missing class {name}'
    cls = classes[name]
    declared_methods = {m.get_name() for m in cls.get_methods()}
    declared_fields = {f.get_name() for f in cls.get_fields()}
    assert methods <= declared_methods, f'{name}: missing methods {methods-declared_methods}'
    assert fields <= declared_fields, f'{name}: missing fields {fields-declared_fields}'
    count += len(methods)+len(fields)
# Establish that our endpoint hooks are still called by the vendor animation choreography.
animator = classes[base+'anim/VolumeItemAnimator;']
for method in ('animateAddImpl','animateMoveImpl'):
    body = next(m for m in animator.get_methods() if m.get_name()==method)
    instructions = '\n'.join(i.get_output() for i in body.get_instructions())
    for call in ('getEndX(', 'getTranslationY(', 'resolveExpandItemDurationMs('):
        assert call in instructions, f'Native animation chain changed: {method} -> {call}'
print(f'Validated {count} native volume method/field targets and vendor expansion animation calls.')
