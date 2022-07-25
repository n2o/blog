import { loadFile } from "nbb";

const {build} = await loadFile("./build.cljs");

build();
