// priority: -100000
const verificationOutputs = ["industrial_steam_platform", "large_furnace", "lv_wireless_charger", "mv_wireless_charger", "processing_plus", "assembling_plus", "separating_plus", "mixing_plus", "universal_joint_factory", "basic_ore_processing_plant", "plasma_machine_tool", "hadron_catalytic_refinery", "quantum_mass_spectrum_array", "superconducting_fusion_assembler", "circuit_encoder_hatch", "pattern_generator", "claim_replacement_terminal", "me_drive", "quantum_data_access_hatch", "weather_anchor", "neutron_control_factory", "infinity_singularity_compressor", "causality_terminal", "lv_crystal_resonator", "mv_crystal_resonator", "hv_crystal_resonator", "ev_crystal_resonator", "iv_crystal_resonator", "luv_crystal_resonator", "zpm_crystal_resonator", "uv_crystal_resonator", "uhv_crystal_resonator", "uev_crystal_resonator", "uiv_crystal_resonator", "uxv_crystal_resonator", "opv_crystal_resonator", "max_crystal_resonator"]
ServerEvents.recipes(event => {
    var verified = 0
    verificationOutputs.forEach(path => {
        var output = 'gtl_enhancedcore:' + path
        var found = 0
        event.forEachRecipe({output: output}, recipe => { found++ })
        if (found !== 1) throw new Error('[EnhancedcoreVerification] expected one recipe for ' + output + ', got ' + found)
        verified++
    })
    var researchVerified = 0
    var replacements = {processing_plant:'processing_plus',assemble_plant:'assembling_plus',separated_plant:'separating_plus',mixed_plant:'mixing_plus'}
    Object.keys(replacements).forEach(old => {
        event.forEachRecipe({id: 'gtceu:research_station/1_x_gtceu_' + old}, recipe => {
            if (!recipe.json.get('inputs').toString().includes('gtl_enhancedcore:' + replacements[old]))
                throw new Error('[EnhancedcoreVerification] research input not migrated ' + old)
            researchVerified++
        })
    })
    if (researchVerified !== 4) throw new Error('[EnhancedcoreVerification] missing plant research recipes: ' + researchVerified)
    var Ultimate = Java.loadClass('org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior$AutoBuildSetting')
    var Advanced = Java.loadClass('com.hepdd.gtmthings.common.item.AdvancedTerminalBehavior$AutoBuildSetting')
    var Info = Java.loadClass('com.lowdragmc.lowdraglib.utils.BlockInfo')
    var Registries = Java.loadClass('net.minecraftforge.registries.ForgeRegistries')
    var Location = Java.loadClass('net.minecraft.resources.ResourceLocation')
    var Stacks = Java.loadClass('net.minecraft.world.item.ItemStack')
    var colors = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
    var combinations = 0
    colors.forEach(color => {
        ['', 'borderless_'].forEach(border => {
            var id = 'gtceu:' + color + '_' + border + 'lamp'
            var lamp = Registries.BLOCKS.getValue(new Location(id))
            var info = Info.fromBlock(lamp)
            ;[new Ultimate(),new Advanced()].forEach(setting => {
                var candidates = setting.apply([info])
                if (candidates.size() !== 9) throw new Error('[EnhancedcoreVerification] invalid lamp candidates ' + id + ': ' + candidates.size())
                for (var variant = 0; variant < 8; variant++) {
                    var actual = lamp.getStackFromIndex(variant)
                    var found = false
                    candidates.forEach(candidate => { if (Stacks.isSameItemSameTags(candidate, actual)) found = true })
                    if (!found) throw new Error('[EnhancedcoreVerification] missing lamp variant ' + id + ':' + variant)
                    combinations++
                }
            })
        })
    })
    console.info('[EnhancedcoreVerification] PASS recipes=' + verified + ' research=' + researchVerified + ' terminal_lamp_combinations=' + combinations)
})

ServerEvents.loaded(event => {
    var forge = Java.loadClass('net.minecraftforge.registries.ForgeRegistries')
    var registries = Java.loadClass('net.minecraft.core.registries.Registries')
    var tagKey = Java.loadClass('net.minecraft.tags.TagKey')
    var location = Java.loadClass('net.minecraft.resources.ResourceLocation')
    var tags = ["gtceu:circuits/ev", "gtceu:circuits/hv", "gtceu:circuits/iv", "gtceu:circuits/luv", "gtceu:circuits/lv", "gtceu:circuits/max", "gtceu:circuits/mv", "gtceu:circuits/opv", "gtceu:circuits/uev", "gtceu:circuits/uhv", "gtceu:circuits/uiv", "gtceu:circuits/uv", "gtceu:circuits/uxv", "gtceu:circuits/zpm"]
    tags.forEach(id => {
        if (forge.ITEMS.tags().getTag(tagKey.create(registries.ITEM, new location(id))).isEmpty())
            throw new Error('[EnhancedcoreVerification] empty ingredient tag ' + id)
    })
    console.info('[EnhancedcoreVerification] PASS ingredient_tags=' + tags.length)
})
