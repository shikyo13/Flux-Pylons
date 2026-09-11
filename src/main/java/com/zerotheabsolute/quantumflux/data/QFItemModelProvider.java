package com.zerotheabsolute.quantumflux.data;

import com.zerotheabsolute.quantumflux.QuantumFlux;
import com.zerotheabsolute.quantumflux.init.QFBlocks;
import com.zerotheabsolute.quantumflux.init.QFItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class QFItemModelProvider extends ItemModelProvider {

    public QFItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, QuantumFlux.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // Instrument geometry is generated from tools/generate_models.py.
        for (String type : new String[]{"range", "capacity", "throughput", "buffer"}) {
            withExistingParent(type + "_upgrade", mcLoc("item/generated"))
                    .texture("layer0", modLoc("item/" + type + "_upgrade"));
        }
    }
}
