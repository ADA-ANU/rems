CREATE OR REPLACE FUNCTION update_comanage_sqlsource()
RETURNS TRIGGER AS $$
BEGIN
    -- First, try to update an existing record
    UPDATE comanage_sqlsource
    SET
        honorific = NEW.data->>'honorific',
        given = NEW.data->>'given-name',
        middle = NEW.data->>'middle-name',
        family = NEW.data->>'family-name',
        suffix = NEW.data->>'suffix',
        affiliation = NEW.data->>'affiliation',
        date_of_birth = NULLIF((NEW.data->>'date-of-birth')::text,'')::date,
        valid_from = NULLIF((NEW.data->>'valid-from')::text,'')::timestamp without time zone,
        valid_through = NULLIF((NEW.data->>'valid-through')::text,'')::timestamp without time zone,
        title = NEW.data->>'title',
        o = NEW.data->>'o',
        ou = NEW.data->>'ou',
        manager_identifier = NEW.data->>'manager-identifier',
        sponsor_identifier = NEW.data->>'sponsor-identifier',
        mail = NEW.data->>'mail',
        identifier = NEW.data->>'identifier',
        telephone_number = NEW.data->>'telephone-number',
        address = NEW.data->>'address',
        url = NEW.data->>'url',
        modified = NOW()
    WHERE sorid = CONCAT(NEW.organization_short_name, '-', NEW.partner_platform_user_id);

    -- If no record was updated, insert a new one
    IF NOT FOUND THEN
        INSERT INTO comanage_sqlsource (
            sorid, honorific, given, middle, family, suffix, affiliation,
            date_of_birth, valid_from, valid_through, title, o, ou,
            manager_identifier, sponsor_identifier, mail, identifier,
            telephone_number, address, url, modified
        )
        VALUES (
            CONCAT(NEW.organization_short_name, '-', NEW.partner_platform_user_id),
            NEW.data->>'honorific', NEW.data->>'given-name', NEW.data->>'middle-name',
            NEW.data->>'family-name', NEW.data->>'suffix', NEW.data->>'affiliation',
            NULLIF((NEW.data->>'date-of-birth')::text,'')::date, NULLIF((NEW.data->>'valid-from')::text,'')::timestamp without time zone,
            NULLIF((NEW.data->>'valid-through')::text,'')::timestamp without time zone,
            NEW.data->>'title', NEW.data->>'o', NEW.data->>'ou', NEW.data->>'manager-identifier',
            NEW.data->>'sponsor-identifier', NEW.data->>'mail', NEW.data->>'identifier',
            NEW.data->>'telephone-number', NEW.data->>'address', NEW.data->>'url', NOW()
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
