# visualization schema change Log

## [1.0.0]
- 2024-??-??
Initial release in the CMR.

----
Using version 
https://git.earthdata.nasa.gov/projects/VISLABS/repos/metadata-mapping/browse/umm/visualization/v0.10.1 as of 08/29/2024

Changes to main.json:
- Changed the name of Identifier to Id
- Added Name to the schema. - Not sure if generics needs this - cant I just copy the title to Name?
- Changed ScienceKeywords array minItems from 0 to 1
- Fixed the reference of SpatialExtentType to spatial-temporal-extent.json
- Moved the reference of TemporalExtentType to spatial-temporal-extent.json
- ConceptIds moved additionalProperties: false up to the type: object declaration
- MetadataSpecification is above the what and how.
- required 
  - Identifier changed name to id
  - Name has been added - can this be Title?
  - ConceptIds was added
  - MetadataSpecification was added
- In the allOf section the What refernces are different - they don't look correct in the given schema
- In the allOf section the How refernces are different - they don't look correct in the given schema
- The rest is just different.

Added the UMM-C 1.18.1 umm-cmn-json-schema.json Spatial, Temporal, and supporting elements to spatial-temporal-extent.json
Removed the older umm-cmn-json-schema.json. 

## [1.1.0]
- 2025-02-06

----
Using version
https://git.earthdata.nasa.gov/projects/VISLABS/repos/metadata-mapping/browse/umm/visualization/v0.11.0 as of 02/06/2025

Changes:
- Schema files are organized into two levels using subdirectories.
- Each subdirectory corresponds to a VisualizationType.
- Subdirectory ./tiles is for VisualizationType "tiles". Subdirectory ./maps, currently a placeholder, for VisualizationType "maps".
- Under each directory, there are two files called schema.json and definitions.json.
- In ./tiles/schema.json, many fields are included. They are from
  - GIBS ICD
  - Worldview Layer Metadata
  - OGC OWS and WMTS xsd
  - Discussions with stakeholders
- In ./tiles/schema.json, potentially redundant fields are tagged with "deprecated" as true. Some fields are labeled as "Experiemental" in comment. These are subject to change after further discussion among stakeholders.

Copyright © 2024-2024 United States Government as represented by the
Administrator of the National Aeronautics and Space Administration. All Rights
Reserved.
